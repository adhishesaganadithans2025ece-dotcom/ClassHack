package com.classhack;

import com.classhack.config.DataInitializer;
import com.classhack.dto.AdminRegisterRequest;
import com.classhack.dto.AdminResponse;
import com.classhack.dto.LoginResponse;
import com.classhack.entity.Admin;
import com.classhack.entity.Student;
import com.classhack.entity.enums.Gender;
import com.classhack.entity.enums.Role;
import com.classhack.exception.BadRequestException;
import com.classhack.repository.AdminRepository;
import com.classhack.repository.StudentRepository;
import com.classhack.security.CustomUserDetails;
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

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminRegistrationAuthTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private AdminService adminService;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataInitializer dataInitializer;

    private Admin superAdmin;

    @BeforeEach
    void setUp() {
        dataInitializer.initializeSuperAdmin();
        superAdmin = adminRepository.findByEmailIgnoreCase(DataInitializer.SUPER_ADMIN_EMAIL)
                .orElseThrow();
    }

    private void authenticateUser(Admin admin) {
        CustomUserDetails userDetails = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority(admin.getRole().name())))
        );
    }

    @Test
    @DisplayName("Super admin is initialized with email admin2244@gmail.com and password admin&2244")
    void testSuperAdminExists() {
        Optional<Admin> superAdminOpt = adminRepository.findByEmailIgnoreCase("admin2244@gmail.com");
        assertTrue(superAdminOpt.isPresent(), "Super admin must exist in the database");
        Admin admin = superAdminOpt.get();
        assertEquals("admin2244@gmail.com", admin.getEmail());
        assertTrue(admin.isActive());
        assertEquals(Role.ROLE_ADMIN, admin.getRole());
        assertTrue(passwordEncoder.matches("admin&2244", admin.getPassword()), "Super admin password must match admin&2244");
    }

    @Test
    @DisplayName("Super admin can register another admin")
    void testSuperAdminCanRegisterAnotherAdmin() {
        AdminRegisterRequest req = AdminRegisterRequest.builder()
                .fullName("Dr. Sharma")
                .phone("9123456780")
                .email("sharma@college.edu")
                .password("sharma@123")
                .department("ECE")
                .section("A")
                .position("Class Coordinator")
                .authorizerEmail("admin2244@gmail.com")
                .authorizerPassword("admin&2244")
                .build();

        LoginResponse res = authService.registerAdmin(req);
        assertNotNull(res);
        assertTrue(res.isSuccess());

        Optional<Admin> newAdminOpt = adminRepository.findByEmailIgnoreCase("sharma@college.edu");
        assertTrue(newAdminOpt.isPresent());
        Admin newAdmin = newAdminOpt.get();
        assertEquals("Dr. Sharma", newAdmin.getFullName());
        assertEquals(Role.ROLE_ADMIN, newAdmin.getRole());
        assertTrue(passwordEncoder.matches("sharma@123", newAdmin.getPassword()));
    }

    @Test
    @DisplayName("Admin created by super admin can also register another admin")
    void testAdminCreatedBySuperAdminCanRegisterAnotherAdmin() {
        // Step 1: Super admin registers Admin 1
        AdminRegisterRequest req1 = AdminRegisterRequest.builder()
                .fullName("Prof. Ramesh")
                .phone("9876543211")
                .email("ramesh@college.edu")
                .password("ramesh&pass1")
                .department("CSE")
                .section("B")
                .position("Faculty")
                .authorizerEmail("admin2244@gmail.com")
                .authorizerPassword("admin&2244")
                .build();

        LoginResponse res1 = authService.registerAdmin(req1);
        assertTrue(res1.isSuccess());

        // Step 2: Admin 1 registers Admin 2
        AdminRegisterRequest req2 = AdminRegisterRequest.builder()
                .fullName("Dr. Meena")
                .phone("9876543212")
                .email("meena@college.edu")
                .password("meena&pass2")
                .department("IT")
                .section("A")
                .position("Innovation Coordinator")
                .authorizerEmail("ramesh@college.edu")
                .authorizerPassword("ramesh&pass1")
                .build();

        LoginResponse res2 = authService.registerAdmin(req2);
        assertNotNull(res2);
        assertTrue(res2.isSuccess());

        Optional<Admin> admin2Opt = adminRepository.findByEmailIgnoreCase("meena@college.edu");
        assertTrue(admin2Opt.isPresent());
        assertEquals("Dr. Meena", admin2Opt.get().getFullName());
    }

    @Test
    @DisplayName("Non-admin person is restricted from registering an admin")
    void testNonAdminCannotRegisterAdmin() {
        studentRepository.save(Student.builder()
                .studentId("CSE999")
                .fullName("Normal Student")
                .gender(Gender.MALE)
                .department("CSE")
                .section("A")
                .year("III")
                .academicYear("2026-27")
                .email("student@college.edu")
                .password(passwordEncoder.encode("studentPass123"))
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        AdminRegisterRequest req = AdminRegisterRequest.builder()
                .fullName("Hacker Admin")
                .phone("9999999999")
                .email("fakeadmin@college.edu")
                .password("hackpass")
                .department("CSE")
                .section("A")
                .position("Fake Admin")
                .authorizerEmail("student@college.edu")
                .authorizerPassword("studentPass123")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.registerAdmin(req)
        );
        assertTrue(ex.getMessage().contains("Registration restricted"));
    }

    @Test
    @DisplayName("Admin with wrong password is restricted from registering an admin")
    void testWrongPasswordRestrictsRegistration() {
        AdminRegisterRequest req = AdminRegisterRequest.builder()
                .fullName("Dr. Wrong")
                .phone("9123456789")
                .email("wrong@college.edu")
                .password("wrong123")
                .department("ECE")
                .section("A")
                .position("Faculty")
                .authorizerEmail("admin2244@gmail.com")
                .authorizerPassword("incorrectPassword!")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.registerAdmin(req)
        );
        assertTrue(ex.getMessage().contains("Registration restricted"));
    }

    @Test
    @DisplayName("verifyAdmin endpoint validates admin credentials")
    void testVerifyAdmin() {
        Map<String, Object> validRes = authService.verifyAdmin("admin2244@gmail.com", "admin&2244");
        assertEquals(true, validRes.get("isAdmin"));

        Map<String, Object> invalidPassRes = authService.verifyAdmin("admin2244@gmail.com", "wrongPass");
        assertEquals(false, invalidPassRes.get("isAdmin"));

        Map<String, Object> unknownRes = authService.verifyAdmin("nobody@gmail.com", "anyPass");
        assertEquals(false, unknownRes.get("isAdmin"));
    }

    @Test
    @DisplayName("Super admin can view all registered admins")
    void testSuperAdminCanViewAllAdmins() {
        authenticateUser(superAdmin);

        // Create an additional admin
        adminRepository.save(Admin.builder()
                .fullName("Prof. Anand")
                .email("anand@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .department("CSE")
                .section("A")
                .position("Faculty")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());

        List<AdminResponse> admins = adminService.getAllAdmins();
        assertNotNull(admins);
        assertTrue(admins.size() >= 2);
        assertTrue(admins.stream().anyMatch(a -> a.getEmail().equalsIgnoreCase(DataInitializer.SUPER_ADMIN_EMAIL)));
        assertTrue(admins.stream().anyMatch(a -> a.getEmail().equalsIgnoreCase("anand@college.edu")));
    }

    @Test
    @DisplayName("Super admin can delete an existing admin")
    void testSuperAdminCanDeleteExistingAdmin() {
        authenticateUser(superAdmin);

        Admin targetAdmin = adminRepository.save(Admin.builder()
                .fullName("To Be Deleted")
                .email("delete.me@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .department("MECH")
                .section("B")
                .position("Coordinator")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());

        Long targetId = targetAdmin.getId();
        assertTrue(adminRepository.existsById(targetId));

        adminService.deleteAdmin(targetId);

        assertFalse(adminRepository.existsById(targetId), "Admin should be deleted from repository");
    }

    @Test
    @DisplayName("Super admin cannot delete itself")
    void testSuperAdminCannotDeleteItself() {
        authenticateUser(superAdmin);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                adminService.deleteAdmin(superAdmin.getId())
        );
        assertTrue(ex.getMessage().contains("Cannot delete the Super Administrator account"));
    }

    @Test
    @DisplayName("Regular admin cannot view all admins")
    void testRegularAdminCannotViewAdmins() {
        Admin regularAdmin = adminRepository.save(Admin.builder()
                .fullName("Regular Faculty")
                .email("regular.admin@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .department("ECE")
                .section("A")
                .position("Faculty")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());

        authenticateUser(regularAdmin);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                adminService.getAllAdmins()
        );
        assertTrue(ex.getMessage().contains("Access Denied: Only the Super Administrator"));
    }

    @Test
    @DisplayName("Regular admin cannot delete another admin")
    void testRegularAdminCannotDeleteAdmin() {
        Admin regularAdmin = adminRepository.save(Admin.builder()
                .fullName("Regular Faculty")
                .email("regular.admin2@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .department("ECE")
                .section("A")
                .position("Faculty")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());

        Admin victim = adminRepository.save(Admin.builder()
                .fullName("Victim Admin")
                .email("victim@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .department("ECE")
                .section("B")
                .position("Faculty")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());

        authenticateUser(regularAdmin);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                adminService.deleteAdmin(victim.getId())
        );
        assertTrue(ex.getMessage().contains("Access Denied: Only the Super Administrator"));
        assertTrue(adminRepository.existsById(victim.getId()), "Victim admin must not be deleted");
    }

    @Test
    @DisplayName("All six simplified admin roles can be registered successfully")
    void testAllSixSimplifiedRolesCanBeRegistered() {
        List<String> roles = List.of(
                "Innovation Coordinator",
                "Class Advisor",
                "Faculty",
                "Class Coordinator",
                "Placement Coordinator",
                "HOD"
        );

        int count = 1;
        for (String role : roles) {
            AdminRegisterRequest req = AdminRegisterRequest.builder()
                    .fullName("Admin " + role)
                    .phone("980000000" + count)
                    .email("admin" + count + "@college.edu")
                    .password("password123")
                    .department("ECE")
                    .section("A")
                    .position(role)
                    .authorizerEmail("admin2244@gmail.com")
                    .authorizerPassword("admin&2244")
                    .build();

            LoginResponse res = authService.registerAdmin(req);
            assertNotNull(res);
            assertTrue(res.isSuccess());

            Admin saved = adminRepository.findByEmailIgnoreCase("admin" + count + "@college.edu").orElseThrow();
            assertEquals(role, saved.getPosition());
            assertEquals(Role.ROLE_ADMIN, saved.getRole());
            count++;
        }
    }

    @Test
    @DisplayName("Invalid admin role is rejected with BadRequestException")
    void testInvalidAdminRoleIsRejected() {
        AdminRegisterRequest req = AdminRegisterRequest.builder()
                .fullName("Invalid Role Admin")
                .phone("9812345678")
                .email("invalidrole@college.edu")
                .password("password123")
                .department("ECE")
                .section("A")
                .position("Department Coordinator") // Not in the 6 simplified roles
                .authorizerEmail("admin2244@gmail.com")
                .authorizerPassword("admin&2244")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                authService.registerAdmin(req)
        );
        assertTrue(ex.getMessage().contains("Invalid role selected"));
    }
}
