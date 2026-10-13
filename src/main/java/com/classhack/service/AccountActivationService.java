package com.classhack.service;

import com.classhack.config.DataInitializer;
import com.classhack.dto.AccountActivationRequest;
import com.classhack.dto.TokenVerificationResponse;
import com.classhack.entity.ActivationToken;
import com.classhack.entity.Admin;
import com.classhack.entity.AuditLog;
import com.classhack.entity.Student;
import com.classhack.entity.enums.Role;
import com.classhack.exception.BadRequestException;
import com.classhack.exception.ResourceNotFoundException;
import com.classhack.repository.ActivationTokenRepository;
import com.classhack.repository.AdminRepository;
import com.classhack.repository.AuditLogRepository;
import com.classhack.repository.StudentRepository;
import com.classhack.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountActivationService {

    private static final int TOKEN_BYTE_LENGTH = 32;
    private static final int TOKEN_EXPIRY_HOURS = 24;
    private static final int RESEND_COOLDOWN_SECONDS = 60;

    private final ActivationTokenRepository activationTokenRepository;
    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String createActivationToken(String email, Role role) {
        String cleanEmail = email.trim().toLowerCase();

        // Invalidate previous unconsumed tokens for this user
        List<ActivationToken> existingTokens = activationTokenRepository.findByEmailIgnoreCaseAndUsedFalse(cleanEmail);
        for (ActivationToken t : existingTokens) {
            t.setUsed(true);
        }
        activationTokenRepository.saveAll(existingTokens);

        // Generate 32 cryptographically random bytes
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(randomBytes);
        String rawToken = HexFormat.of().formatHex(randomBytes);

        String tokenHash = hashToken(rawToken);

        ActivationToken activationToken = ActivationToken.builder()
                .tokenHash(tokenHash)
                .email(cleanEmail)
                .role(role)
                .expiryTime(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS))
                .used(false)
                .build();

        activationTokenRepository.save(activationToken);

        return rawToken;
    }

    public TokenVerificationResponse verifyActivationToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Activation token is required.");
        }

        String tokenHash = hashToken(rawToken.trim());
        ActivationToken token = activationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("This activation link is invalid, expired, or has already been used."));

        if (token.isUsed()) {
            throw new BadRequestException("This activation link has already been used. Please log in with your credentials or contact your administrator.");
        }

        if (token.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This activation link has expired. Please contact your administrator to request a new link.");
        }

        if (token.getRole() == Role.ROLE_STUDENT) {
            Student student = studentRepository.findByEmailIgnoreCase(token.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Student account not found for this activation link."));
            return TokenVerificationResponse.builder()
                    .valid(true)
                    .email(student.getEmail())
                    .fullName(student.getFullName())
                    .role("STUDENT")
                    .designationOrId(student.getRollNumber())
                    .message("Token is valid. You may set your password.")
                    .build();
        } else {
            Admin admin = adminRepository.findByEmailIgnoreCase(token.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Administrator account not found for this activation link."));
            return TokenVerificationResponse.builder()
                    .valid(true)
                    .email(admin.getEmail())
                    .fullName(admin.getFullName())
                    .role("ADMIN")
                    .designationOrId(admin.getPosition())
                    .message("Token is valid. You may set your password.")
                    .build();
        }
    }

    @Transactional
    public void activateAccount(AccountActivationRequest req) {
        if (req.getToken() == null || req.getToken().isBlank()) {
            throw new BadRequestException("Activation token is required.");
        }

        if (req.getNewPassword() == null || req.getNewPassword().length() < 6) {
            throw new BadRequestException("Password must be at least 6 characters long.");
        }

        if (!req.getNewPassword().equals(req.getConfirmPassword())) {
            throw new BadRequestException("Passwords do not match.");
        }

        String tokenHash = hashToken(req.getToken().trim());
        ActivationToken token = activationTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new BadRequestException("This activation link is invalid or has expired."));

        if (token.isUsed()) {
            throw new BadRequestException("This activation link has already been used.");
        }

        if (token.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("This activation link has expired.");
        }

        String encodedPassword = passwordEncoder.encode(req.getNewPassword());

        if (token.getRole() == Role.ROLE_STUDENT) {
            Student student = studentRepository.findByEmailIgnoreCase(token.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Student account not found."));
            student.setPassword(encodedPassword);
            student.setActivated(true);
            student.setActive(true);
            studentRepository.save(student);
        } else {
            Admin admin = adminRepository.findByEmailIgnoreCase(token.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("Administrator account not found."));
            admin.setPassword(encodedPassword);
            admin.setActivated(true);
            admin.setActive(true);
            adminRepository.save(admin);
        }

        token.setUsed(true);
        activationTokenRepository.save(token);

        auditLogRepository.save(AuditLog.builder()
                .action("ACCOUNT_ACTIVATED")
                .performedBy(token.getEmail())
                .details("Account successfully activated and password established for " + token.getEmail() + " (" + token.getRole() + ")")
                .build());

        log.info("Account successfully activated for user: {}", maskEmail(token.getEmail()));
    }

    @Transactional
    public EmailService.EmailResult resendStudentActivation(Long studentId, CustomUserDetails admin) {
        if (admin == null) {
            throw new BadRequestException("User not authenticated.");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        // Enforce department access
        boolean isSuperAdmin = DataInitializer.SUPER_ADMIN_EMAIL.equalsIgnoreCase(admin.getEmail()) ||
                "CENTRAL".equalsIgnoreCase(admin.getDepartment()) ||
                "ALL".equalsIgnoreCase(admin.getDepartment());

        if (!isSuperAdmin) {
            if (!student.getDepartment().equalsIgnoreCase(admin.getDepartment()) ||
                !student.getSection().equalsIgnoreCase(admin.getSection())) {
                throw new BadRequestException("Access Denied: You cannot manage students outside your assigned class.");
            }
        }

        checkRateLimit(student.getEmail());

        String rawToken = createActivationToken(student.getEmail(), Role.ROLE_STUDENT);
        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                student.getFullName(),
                student.getRollNumber(),
                student.getEmail(),
                rawToken
        );

        auditLogRepository.save(AuditLog.builder()
                .action("STUDENT_ACTIVATION_RESENT")
                .performedBy(admin.getEmail())
                .details("Resent activation email to student: " + student.getEmail() + " (Success: " + result.success() + ")")
                .build());

        return result;
    }

    @Transactional
    public EmailService.EmailResult resendAdminActivation(String email, CustomUserDetails currentAdmin) {
        if (currentAdmin == null) {
            throw new BadRequestException("User not authenticated.");
        }

        Admin targetAdmin = adminRepository.findByEmailIgnoreCase(email.trim().toLowerCase())
                .orElseThrow(() -> new ResourceNotFoundException("Administrator not found with email: " + email));

        checkRateLimit(targetAdmin.getEmail());

        String rawToken = createActivationToken(targetAdmin.getEmail(), Role.ROLE_ADMIN);
        EmailService.EmailResult result = emailService.sendAdminWelcomeEmail(
                targetAdmin.getFullName(),
                targetAdmin.getPosition(),
                targetAdmin.getEmail(),
                rawToken
        );

        auditLogRepository.save(AuditLog.builder()
                .action("ADMIN_ACTIVATION_RESENT")
                .performedBy(currentAdmin.getEmail())
                .details("Resent activation email to admin: " + targetAdmin.getEmail() + " (Success: " + result.success() + ")")
                .build());

        return result;
    }

    private void checkRateLimit(String email) {
        Optional<ActivationToken> latest = activationTokenRepository.findTopByEmailIgnoreCaseOrderByCreatedAtDesc(email);
        if (latest.isPresent() && latest.get().getCreatedAt() != null) {
            long secondsAgo = Duration.between(latest.get().getCreatedAt(), LocalDateTime.now()).getSeconds();
            if (secondsAgo < RESEND_COOLDOWN_SECONDS) {
                throw new BadRequestException("Please wait at least " + (RESEND_COOLDOWN_SECONDS - secondsAgo) + " seconds before requesting another activation email.");
            }
        }
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return email;
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
