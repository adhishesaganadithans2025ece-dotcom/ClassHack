package com.classhack.service;

import com.classhack.dto.AdminRegisterRequest;
import com.classhack.dto.ChangePasswordRequest;
import com.classhack.dto.LoginRequest;
import com.classhack.dto.LoginResponse;
import com.classhack.entity.Admin;
import com.classhack.entity.AuditLog;
import com.classhack.entity.Student;
import com.classhack.entity.enums.Role;
import com.classhack.exception.BadRequestException;
import com.classhack.exception.DuplicateResourceException;
import com.classhack.exception.ResourceNotFoundException;
import com.classhack.repository.AdminRepository;
import com.classhack.repository.AuditLogRepository;
import com.classhack.repository.StudentRepository;
import com.classhack.security.CustomUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final Set<String> ALLOWED_ADMIN_ROLES = Set.of(
            "Innovation Coordinator",
            "Class Advisor",
            "Faculty",
            "Class Coordinator",
            "Placement Coordinator",
            "HOD"
    );

    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    @Transactional
    public LoginResponse registerAdmin(AdminRegisterRequest req) {
        // 1. Authorize: Verify that the person attempting to register the new admin is an active admin
        String authorizerEmail = req.getAuthorizerEmail() != null ? req.getAuthorizerEmail().trim().toLowerCase() : "";
        String authorizerPassword = req.getAuthorizerPassword();

        if (authorizerEmail.isBlank() || authorizerPassword == null || authorizerPassword.isBlank()) {
            throw new BadRequestException("Registration restricted: Authorizing administrator email and password are required.");
        }

        Admin authorizer = adminRepository.findByEmailIgnoreCase(authorizerEmail)
                .orElseThrow(() -> new BadRequestException(
                        "Registration restricted: Only verified administrators can register a new admin. The email '" + authorizerEmail + "' is not an administrator."));

        if (!passwordEncoder.matches(authorizerPassword, authorizer.getPassword())) {
            throw new BadRequestException(
                    "Registration restricted: Invalid administrator credentials. Only an existing administrator can authorize creating another admin.");
        }

        if (!authorizer.isActive() || authorizer.getRole() != Role.ROLE_ADMIN) {
            throw new BadRequestException(
                    "Registration restricted: The authorizing administrator account is not active.");
        }

        // 2. Validate new admin details
        String email = req.getEmail().trim().toLowerCase();

        if (adminRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("An administrator with email " + email + " already exists.");
        }
        if (studentRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("This email is already registered in the system.");
        }

        String position = req.getPosition() != null ? req.getPosition().trim() : "";
        if (!ALLOWED_ADMIN_ROLES.contains(position)) {
            throw new BadRequestException("Invalid role selected. Allowed roles are: " + String.join(", ", ALLOWED_ADMIN_ROLES));
        }

        Admin admin = Admin.builder()
                .fullName(req.getFullName().trim())
                .phone(req.getPhone() != null ? req.getPhone().trim() : null)
                .email(email)
                .password(passwordEncoder.encode(req.getPassword()))
                .department(req.getDepartment().trim().toUpperCase())
                .section(req.getSection().trim().toUpperCase())
                .position(position)
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build();

        Admin saved = adminRepository.save(admin);

        auditLogRepository.save(AuditLog.builder()
                .action("ADMIN_REGISTERED")
                .performedBy(authorizer.getEmail())
                .details("Admin registered: " + saved.getFullName() + " (" + saved.getPosition() + ", " + saved.getDepartment() + "-" + saved.getSection() + ") authorized by " + authorizer.getEmail())
                .build());

        return LoginResponse.builder()
                .success(true)
                .message("Administrator account registered successfully! You may now login.")
                .role(saved.getRole().name())
                .fullName(saved.getFullName())
                .email(saved.getEmail())
                .department(saved.getDepartment())
                .section(saved.getSection())
                .redirectUrl("/login.html")
                .build();
    }

    public Map<String, Object> verifyAdmin(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            return Map.of("isAdmin", false, "message", "Email and password are required.");
        }
        String cleanEmail = email.trim().toLowerCase();
        Optional<Admin> adminOpt = adminRepository.findByEmailIgnoreCase(cleanEmail);
        if (adminOpt.isEmpty()) {
            return Map.of("isAdmin", false, "message", "No administrator account found with this email.");
        }
        Admin admin = adminOpt.get();
        if (!passwordEncoder.matches(password, admin.getPassword())) {
            return Map.of("isAdmin", false, "message", "Invalid administrator password.");
        }
        if (!admin.isActive() || admin.getRole() != Role.ROLE_ADMIN) {
            return Map.of("isAdmin", false, "message", "Administrator account is not active.");
        }
        return Map.of(
                "isAdmin", true,
                "message", "Administrator verified successfully.",
                "fullName", admin.getFullName(),
                "email", admin.getEmail(),
                "position", admin.getPosition()
        );
    }

    public LoginResponse login(LoginRequest req, HttpServletRequest request, HttpServletResponse response) {
        String username = req.getUsername().trim();

        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, req.getPassword())
        );

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(auth);
        SecurityContextHolder.setContext(securityContext);
        securityContextRepository.saveContext(securityContext, request, response);

        CustomUserDetails userDetails = (CustomUserDetails) auth.getPrincipal();

        // Enforce loginType if provided by UI tab
        if (req.getLoginType() != null && !req.getLoginType().isBlank()) {
            String expectedRole = "ROLE_" + req.getLoginType().toUpperCase();
            if (!userDetails.getRole().name().equals(expectedRole)) {
                SecurityContextHolder.clearContext();
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.invalidate();
                }
                throw new BadRequestException("Invalid credentials for " + req.getLoginType().toLowerCase() + " login. Please use the appropriate login tab.");
            }
        }

        String redirectUrl = userDetails.getRole() == Role.ROLE_ADMIN ? "/admin/dashboard.html" : "/student/dashboard.html";

        return LoginResponse.builder()
                .success(true)
                .message("Login successful!")
                .role(userDetails.getRole().name())
                .fullName(userDetails.getFullName())
                .email(userDetails.getEmail())
                .studentId(userDetails.getStudentId())
                .department(userDetails.getDepartment())
                .section(userDetails.getSection())
                .redirectUrl(redirectUrl)
                .build();
    }

    public CustomUserDetails getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof CustomUserDetails)) {
            return null;
        }
        return (CustomUserDetails) auth.getPrincipal();
    }

    @Transactional
    public void changePassword(ChangePasswordRequest req) {
        CustomUserDetails userDetails = getCurrentUser();
        if (userDetails == null) {
            throw new BadRequestException("User not authenticated.");
        }

        if (userDetails.getRole() == Role.ROLE_ADMIN) {
            Admin admin = adminRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
            if (!passwordEncoder.matches(req.getCurrentPassword(), admin.getPassword())) {
                throw new BadRequestException("Current password does not match.");
            }
            admin.setPassword(passwordEncoder.encode(req.getNewPassword()));
            adminRepository.save(admin);
        } else {
            Student student = studentRepository.findById(userDetails.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found"));
            if (!passwordEncoder.matches(req.getCurrentPassword(), student.getPassword())) {
                throw new BadRequestException("Current password does not match.");
            }
            student.setPassword(passwordEncoder.encode(req.getNewPassword()));
            studentRepository.save(student);
        }
    }
}
