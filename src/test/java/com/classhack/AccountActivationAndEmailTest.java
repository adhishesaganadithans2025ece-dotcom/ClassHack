package com.classhack;

import com.classhack.config.DataInitializer;
import com.classhack.dto.AccountActivationRequest;
import com.classhack.dto.AdminRegisterRequest;
import com.classhack.dto.LoginResponse;
import com.classhack.dto.StudentCreateRequest;
import com.classhack.dto.StudentResponse;
import com.classhack.dto.TokenVerificationResponse;
import com.classhack.entity.ActivationToken;
import com.classhack.entity.Admin;
import com.classhack.entity.Student;
import com.classhack.entity.enums.Gender;
import com.classhack.entity.enums.Role;
import com.classhack.exception.BadRequestException;
import com.classhack.exception.DuplicateResourceException;
import com.classhack.repository.ActivationTokenRepository;
import com.classhack.repository.AdminRepository;
import com.classhack.repository.StudentRepository;
import com.classhack.security.CustomUserDetails;
import com.classhack.service.AccountActivationService;
import com.classhack.service.AdminService;
import com.classhack.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountActivationAndEmailTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AuthService authService;

    @Autowired
    private AccountActivationService accountActivationService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private ActivationTokenRepository activationTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataInitializer dataInitializer;

    private Admin superAdmin;

    @BeforeEach
    void setUp() {
        dataInitializer.initializeSuperAdmin();
        superAdmin = adminRepository.findByEmailIgnoreCase(DataInitializer.SUPER_ADMIN_EMAIL).orElseThrow();
        authenticateUser(superAdmin);
    }

    private void authenticateUser(Admin admin) {
        CustomUserDetails userDetails = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority(admin.getRole().name())))
        );
    }

    @Test
    @DisplayName("Admin creates student: saves student, securely hashes password, sets active and activated, and requires password change")
    void testStudentCreationGeneratesActivationToken() {
        StudentCreateRequest req = StudentCreateRequest.builder()
                .studentId("ECE101")
                .rollNumber("710021106001")
                .fullName("Aarav Patel")
                .gender(Gender.MALE)
                .department("ECE")
                .section("A")
                .year("III")
                .academicYear("2026-27")
                .phone("9876543210")
                .email("aarav.ece@college.edu")
                .password("tempInitialPass")
                .build();

        StudentResponse res = adminService.createStudent(req);

        assertNotNull(res);
        assertEquals("ECE101", res.getStudentId());
        assertEquals("710021106001", res.getRollNumber());
        assertTrue(res.isActivated(), "Newly created student is activated immediately without requiring email activation");
        assertTrue(res.isPasswordChangeRequired(), "Newly created student requires password change on first login");
        assertNotNull(res.getBatchId(), "Batch ID is provided for credential export");

        Student saved = studentRepository.findByEmailIgnoreCase("aarav.ece@college.edu").orElseThrow();
        assertEquals("710021106001", saved.getRollNumber());
        assertTrue(saved.isActivated());
        assertTrue(saved.isPasswordChangeRequired());

        // Verify password is ONLY stored as secure BCrypt hash in DB, never plaintext
        assertNotEquals("tempInitialPass", saved.getPassword(), "Plaintext password must never be stored in database");
        assertTrue(passwordEncoder.matches("tempInitialPass", saved.getPassword()), "Stored hash must match configured password encoder");
    }

    @Test
    @DisplayName("Student roll number falls back to studentId if rollNumber is null or blank")
    void testStudentRollNumberFallback() {
        Student student = Student.builder()
                .studentId("CSE202")
                .rollNumber(null)
                .fullName("Bhavna Rao")
                .gender(Gender.FEMALE)
                .department("CSE")
                .section("B")
                .year("II")
                .academicYear("2026-27")
                .email("bhavna@college.edu")
                .password(passwordEncoder.encode("secret"))
                .build();

        assertEquals("CSE202", student.getRollNumber(), "Roll number must fall back to studentId when null");

        student.setRollNumber("   ");
        assertEquals("CSE202", student.getRollNumber(), "Roll number must fall back to studentId when blank");

        student.setRollNumber("REG-999");
        assertEquals("REG-999", student.getRollNumber(), "Roll number must return explicit rollNumber when present");
    }

    @Test
    @DisplayName("Student activation flow: verify token, set password, mark activated, invalidate token")
    void testStudentActivationWorkflow() {
        Student student = studentRepository.save(Student.builder()
                .studentId("MECH301")
                .rollNumber("710021108001")
                .fullName("Chetan Sharma")
                .gender(Gender.MALE)
                .department("MECH")
                .section("A")
                .year("IV")
                .academicYear("2026-27")
                .email("chetan@college.edu")
                .password(passwordEncoder.encode("placeholderPass"))
                .active(true)
                .activated(false)
                .build());

        String rawToken = accountActivationService.createActivationToken(student.getEmail(), Role.ROLE_STUDENT);

        // 1. Verify token
        TokenVerificationResponse verifyRes = accountActivationService.verifyActivationToken(rawToken);
        assertTrue(verifyRes.isValid());
        assertEquals("chetan@college.edu", verifyRes.getEmail());
        assertEquals("Chetan Sharma", verifyRes.getFullName());
        assertEquals("STUDENT", verifyRes.getRole());
        assertEquals("710021108001", verifyRes.getDesignationOrId());

        // 2. Set new secure password
        AccountActivationRequest activationReq = AccountActivationRequest.builder()
                .token(rawToken)
                .newPassword("myNewSecurePass123")
                .confirmPassword("myNewSecurePass123")
                .build();

        accountActivationService.activateAccount(activationReq);

        // 3. Verify student updated
        Student updated = studentRepository.findByEmailIgnoreCase("chetan@college.edu").orElseThrow();
        assertTrue(updated.isActivated(), "Student must be marked activated");
        assertTrue(updated.isActive());
        assertTrue(passwordEncoder.matches("myNewSecurePass123", updated.getPassword()), "New password must be securely hashed and match");

        // 4. Verify token marked used
        String tokenHash = AccountActivationService.hashToken(rawToken);
        ActivationToken usedToken = activationTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertTrue(usedToken.isUsed(), "Token must be invalidated immediately after use");

        // 5. Trying to use the token again is rejected
        assertThrows(BadRequestException.class, () -> accountActivationService.activateAccount(activationReq),
                "Used token must be rejected");
        assertThrows(BadRequestException.class, () -> accountActivationService.verifyActivationToken(rawToken),
                "Used token must be rejected during verification");
    }

    @Test
    @DisplayName("Admin registration flow: creates admin, generates token, allows password setup")
    void testAdminRegistrationActivationWorkflow() {
        AdminRegisterRequest req = AdminRegisterRequest.builder()
                .fullName("Dr. Meenakshi")
                .phone("9876543222")
                .email("meenakshi@college.edu")
                .password("adminTempPass")
                .department("ECE")
                .section("A")
                .position("Class Advisor")
                .authorizerEmail(superAdmin.getEmail())
                .authorizerPassword("admin&2244")
                .build();

        LoginResponse res = authService.registerAdmin(req);
        assertTrue(res.isSuccess());

        Admin savedAdmin = adminRepository.findByEmailIgnoreCase("meenakshi@college.edu").orElseThrow();
        assertEquals("Class Advisor", savedAdmin.getPosition());
        assertTrue(savedAdmin.isActivated(), "Registered admin is activated immediately upon creation");

        // Test activation token generation and password reset/setup flow
        String rawToken = accountActivationService.createActivationToken("meenakshi@college.edu", Role.ROLE_ADMIN);

        TokenVerificationResponse verify = accountActivationService.verifyActivationToken(rawToken);
        assertTrue(verify.isValid());
        assertEquals("Class Advisor", verify.getDesignationOrId());

        AccountActivationRequest actReq = AccountActivationRequest.builder()
                .token(rawToken)
                .newPassword("adminNewPass456")
                .confirmPassword("adminNewPass456")
                .build();

        accountActivationService.activateAccount(actReq);

        Admin activatedAdmin = adminRepository.findByEmailIgnoreCase("meenakshi@college.edu").orElseThrow();
        assertTrue(activatedAdmin.isActivated());
        assertTrue(passwordEncoder.matches("adminNewPass456", activatedAdmin.getPassword()));
    }

    @Test
    @DisplayName("Activation token expires: expired token is rejected")
    void testExpiredActivationTokenIsRejected() {
        Student student = studentRepository.save(Student.builder()
                .studentId("IT401")
                .rollNumber("710021105001")
                .fullName("Deepak Kumar")
                .gender(Gender.MALE)
                .department("IT")
                .section("A")
                .year("I")
                .academicYear("2026-27")
                .email("deepak@college.edu")
                .password("somepass")
                .build());

        String rawToken = "sampleExpiredToken12345678901234567890";
        String tokenHash = AccountActivationService.hashToken(rawToken);

        activationTokenRepository.save(ActivationToken.builder()
                .tokenHash(tokenHash)
                .email(student.getEmail())
                .role(Role.ROLE_STUDENT)
                .expiryTime(LocalDateTime.now().minusHours(1)) // Expired
                .used(false)
                .build());

        assertThrows(BadRequestException.class, () -> accountActivationService.verifyActivationToken(rawToken));

        AccountActivationRequest req = AccountActivationRequest.builder()
                .token(rawToken)
                .newPassword("newPassword123")
                .confirmPassword("newPassword123")
                .build();
        assertThrows(BadRequestException.class, () -> accountActivationService.activateAccount(req));
    }

    @Test
    @DisplayName("Rate limiting: requesting resend immediately within cooldown is rejected")
    void testResendRateLimiting() {
        Student student = studentRepository.save(Student.builder()
                .studentId("EEE501")
                .rollNumber("710021103001")
                .fullName("Esha Nair")
                .gender(Gender.FEMALE)
                .department("EEE")
                .section("A")
                .year("III")
                .academicYear("2026-27")
                .email("esha@college.edu")
                .password("somepass")
                .build());

        // First creation
        accountActivationService.createActivationToken(student.getEmail(), Role.ROLE_STUDENT);

        // Immediate resend must be throttled
        assertThrows(BadRequestException.class, () -> accountActivationService.resendStudentActivation(student.getId(), new CustomUserDetails(superAdmin)));
    }

    @Test
    @DisplayName("Password mismatch during activation throws BadRequestException")
    void testPasswordMismatchThrowsException() {
        String rawToken = accountActivationService.createActivationToken("mismatch@test.edu", Role.ROLE_STUDENT);

        AccountActivationRequest req = AccountActivationRequest.builder()
                .token(rawToken)
                .newPassword("password123")
                .confirmPassword("password999") // Mismatched
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> accountActivationService.activateAccount(req));
        assertTrue(ex.getMessage().contains("match"));
    }

    @Test
    @DisplayName("Password shorter than 6 characters throws BadRequestException")
    void testShortPasswordThrowsException() {
        String rawToken = accountActivationService.createActivationToken("short@test.edu", Role.ROLE_STUDENT);

        AccountActivationRequest req = AccountActivationRequest.builder()
                .token(rawToken)
                .newPassword("123") // Too short
                .confirmPassword("123")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> accountActivationService.activateAccount(req));
        assertTrue(ex.getMessage().contains("at least 6 characters"));
    }

    @Test
    @DisplayName("Active student can log in directly with roll number without email activation; deactivated student is rejected")
    void testActiveStudentDirectLoginAndDeactivatedBlocked() {
        Student student = studentRepository.save(Student.builder()
                .studentId("ECE777")
                .rollNumber("710021106777")
                .fullName("Rohan Verma")
                .gender(Gender.MALE)
                .department("ECE")
                .section("A")
                .year("III")
                .academicYear("2026-27")
                .email("rohan.verma@college.edu")
                .password(passwordEncoder.encode("initialTempPass"))
                .active(true)
                .activated(true)
                .passwordChangeRequired(true)
                .build());

        var req = new org.springframework.mock.web.MockHttpServletRequest();
        var res = new org.springframework.mock.web.MockHttpServletResponse();

        // 1. Direct login succeeds with roll number and temporary password
        com.classhack.dto.LoginRequest loginReq = com.classhack.dto.LoginRequest.builder()
                .username("710021106777")
                .password("initialTempPass")
                .loginType("STUDENT")
                .build();

        LoginResponse loginRes = authService.login(loginReq, req, res);
        assertNotNull(loginRes);
        assertTrue(loginRes.isSuccess());
        assertEquals("ROLE_STUDENT", loginRes.getRole());
        assertEquals("rohan.verma@college.edu", loginRes.getEmail());
        assertTrue(loginRes.isPasswordChangeRequired(), "Student requires password change on first login");

        // 2. Deactivated student account (active=false) cannot log in
        student.setActive(false);
        studentRepository.save(student);

        assertThrows(Exception.class, () -> authService.login(loginReq, req, res),
                "Deactivated account must be rejected from login");
    }

    @Test
    @DisplayName("Student search query finds student by roll number")
    void testStudentSearchByRollNumber() {
        studentRepository.save(Student.builder()
                .studentId("CSE888")
                .rollNumber("710021104888")
                .fullName("Pooja Hegde")
                .gender(Gender.FEMALE)
                .department("CSE")
                .section("B")
                .year("II")
                .academicYear("2026-27")
                .email("pooja.hegde@college.edu")
                .password(passwordEncoder.encode("secret"))
                .active(true)
                .activated(true)
                .build());

        List<Student> results = studentRepository.searchStudents("710021104888");
        assertFalse(results.isEmpty(), "Student must be found when searching by roll number");
        assertEquals("CSE888", results.get(0).getStudentId());
    }
}
