package com.classhack;

import com.classhack.config.DataInitializer;
import com.classhack.dto.HackathonCreateRequest;
import com.classhack.dto.HackathonResponse;
import com.classhack.dto.StudentCreateRequest;
import com.classhack.dto.StudentResponse;
import com.classhack.dto.StudentUpdateRequest;
import com.classhack.entity.Admin;
import com.classhack.entity.Student;
import com.classhack.entity.enums.Gender;
import com.classhack.entity.enums.HackathonMode;
import com.classhack.entity.enums.Role;
import com.classhack.exception.BadRequestException;
import com.classhack.exception.DuplicateHackathonException;
import com.classhack.repository.AdminRepository;
import com.classhack.repository.HackathonRepository;
import com.classhack.repository.StudentRepository;
import com.classhack.security.CustomUserDetails;
import com.classhack.service.AdminService;
import com.classhack.service.HackathonService;
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

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AdminDepartmentScopingAndHackathonDuplicateTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private HackathonService hackathonService;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private HackathonRepository hackathonRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataInitializer dataInitializer;

    private Admin superAdmin;
    private Admin adminEceA;
    private Admin adminCseB;

    @BeforeEach
    void setUp() {
        dataInitializer.initializeSuperAdmin();
        superAdmin = adminRepository.findByEmailIgnoreCase(DataInitializer.SUPER_ADMIN_EMAIL)
                .orElseThrow();

        adminEceA = adminRepository.save(Admin.builder()
                .fullName("Prof. ECE Admin")
                .email("ece.admin@college.edu")
                .password(passwordEncoder.encode("ecePass123"))
                .department("ECE")
                .section("A")
                .position("Class Coordinator")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());

        adminCseB = adminRepository.save(Admin.builder()
                .fullName("Prof. CSE Admin")
                .email("cse.admin@college.edu")
                .password(passwordEncoder.encode("csePass123"))
                .department("CSE")
                .section("B")
                .position("Class Coordinator")
                .role(Role.ROLE_ADMIN)
                .active(true)
                .build());
    }

    private void authenticateUser(Admin admin) {
        CustomUserDetails userDetails = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority(admin.getRole().name())))
        );
    }

    @Test
    @DisplayName("Admin can only create students belonging to their own department and section")
    void testAdminDepartmentAndSectionCreationRestriction() {
        authenticateUser(adminEceA);

        // Allowed: Adding student in ECE-A
        StudentCreateRequest validReq = StudentCreateRequest.builder()
                .studentId("ECE101")
                .fullName("John Doe")
                .gender(Gender.MALE)
                .department("ECE")
                .section("A")
                .year("III")
                .academicYear("2026-27")
                .email("john.ece@college.edu")
                .password("student123")
                .build();

        StudentResponse created = adminService.createStudent(validReq);
        assertNotNull(created);
        assertEquals("ECE", created.getDepartment());
        assertEquals("A", created.getSection());

        // Restricted: Admin ECE-A trying to add a student in CSE-B
        StudentCreateRequest invalidReqDept = StudentCreateRequest.builder()
                .studentId("CSE201")
                .fullName("Jane Smith")
                .gender(Gender.FEMALE)
                .department("CSE")
                .section("B")
                .year("III")
                .academicYear("2026-27")
                .email("jane.cse@college.edu")
                .password("student123")
                .build();

        BadRequestException exDept = assertThrows(BadRequestException.class, () ->
                adminService.createStudent(invalidReqDept)
        );
        assertTrue(exDept.getMessage().contains("Access Denied: You can only add students of your assigned department"));

        // Restricted: Admin ECE-A trying to add a student in ECE-B (different section)
        StudentCreateRequest invalidReqSec = StudentCreateRequest.builder()
                .studentId("ECE102")
                .fullName("Bob Lee")
                .gender(Gender.MALE)
                .department("ECE")
                .section("B")
                .year("III")
                .academicYear("2026-27")
                .email("bob.ece@college.edu")
                .password("student123")
                .build();

        BadRequestException exSec = assertThrows(BadRequestException.class, () ->
                adminService.createStudent(invalidReqSec)
        );
        assertTrue(exSec.getMessage().contains("Access Denied: You can only add students of your assigned department"));
    }

    @Test
    @DisplayName("Super admin can create students for any department and section")
    void testSuperAdminCanCreateStudentInAnyDepartment() {
        authenticateUser(superAdmin);

        StudentCreateRequest req = StudentCreateRequest.builder()
                .studentId("MECH301")
                .fullName("David Miller")
                .gender(Gender.MALE)
                .department("MECH")
                .section("C")
                .year("II")
                .academicYear("2026-27")
                .email("david.mech@college.edu")
                .password("student123")
                .build();

        StudentResponse created = adminService.createStudent(req);
        assertNotNull(created);
        assertEquals("MECH", created.getDepartment());
        assertEquals("C", created.getSection());
    }

    @Test
    @DisplayName("Admin cannot view or search students from other departments and sections")
    void testAdminCannotViewOtherDepartmentStudents() {
        // Create student in ECE-A
        Student eceStudent = studentRepository.save(Student.builder()
                .studentId("ECE501")
                .fullName("ECE Student")
                .gender(Gender.MALE)
                .department("ECE")
                .section("A")
                .year("III")
                .academicYear("2026-27")
                .email("ece501@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        // Create student in CSE-B
        Student cseStudent = studentRepository.save(Student.builder()
                .studentId("CSE502")
                .fullName("CSE Student")
                .gender(Gender.FEMALE)
                .department("CSE")
                .section("B")
                .year("III")
                .academicYear("2026-27")
                .email("cse502@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        // Login as ECE-A Admin
        authenticateUser(adminEceA);

        List<StudentResponse> eceAdminStudents = adminService.getAllStudents();
        assertTrue(eceAdminStudents.stream().anyMatch(s -> s.getId().equals(eceStudent.getId())));
        assertFalse(eceAdminStudents.stream().anyMatch(s -> s.getId().equals(cseStudent.getId())),
                "ECE Admin should not be able to view CSE students");

        // Search scoping
        List<StudentResponse> searchResult = adminService.searchStudents("Student");
        assertTrue(searchResult.stream().anyMatch(s -> s.getId().equals(eceStudent.getId())));
        assertFalse(searchResult.stream().anyMatch(s -> s.getId().equals(cseStudent.getId())));

        // Direct get by ID of CSE student must be rejected
        BadRequestException getEx = assertThrows(BadRequestException.class, () ->
                adminService.getStudentById(cseStudent.getId())
        );
        assertTrue(getEx.getMessage().contains("Access Denied"));
    }

    @Test
    @DisplayName("Admin cannot modify, toggle status, or reset password of students from other departments")
    void testAdminCannotModifyOtherDepartmentStudents() {
        Student cseStudent = studentRepository.save(Student.builder()
                .studentId("CSE701")
                .fullName("CSE Target")
                .gender(Gender.FEMALE)
                .department("CSE")
                .section("B")
                .year("IV")
                .academicYear("2026-27")
                .email("cse701@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        // Authenticate as Admin of ECE-A
        authenticateUser(adminEceA);

        // Update attempt
        StudentUpdateRequest updateReq = StudentUpdateRequest.builder()
                .fullName("Modified CSE")
                .department("CSE")
                .section("B")
                .year("IV")
                .academicYear("2026-27")
                .email("cse701@college.edu")
                .build();

        BadRequestException updateEx = assertThrows(BadRequestException.class, () ->
                adminService.updateStudent(cseStudent.getId(), updateReq)
        );
        assertTrue(updateEx.getMessage().contains("Access Denied"));

        // Toggle status attempt
        BadRequestException toggleEx = assertThrows(BadRequestException.class, () ->
                adminService.toggleStudentStatus(cseStudent.getId())
        );
        assertTrue(toggleEx.getMessage().contains("Access Denied"));

        // Reset password attempt
        BadRequestException resetEx = assertThrows(BadRequestException.class, () ->
                adminService.resetStudentPassword(cseStudent.getId(), "newsecretpass")
        );
        assertTrue(resetEx.getMessage().contains("Access Denied"));
    }

    @Test
    @DisplayName("Admin cannot transfer a student to another department or section")
    void testAdminCannotTransferStudentToAnotherClass() {
        Student eceStudent = studentRepository.save(Student.builder()
                .studentId("ECE801")
                .fullName("ECE Move")
                .gender(Gender.MALE)
                .department("ECE")
                .section("A")
                .year("II")
                .academicYear("2026-27")
                .email("ece801@college.edu")
                .password(passwordEncoder.encode("pass123"))
                .role(Role.ROLE_STUDENT)
                .active(true)
                .build());

        authenticateUser(adminEceA);

        StudentUpdateRequest transferReq = StudentUpdateRequest.builder()
                .fullName("ECE Move")
                .department("IT")
                .section("A")
                .year("II")
                .academicYear("2026-27")
                .email("ece801@college.edu")
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                adminService.updateStudent(eceStudent.getId(), transferReq)
        );
        assertTrue(ex.getMessage().contains("Access Denied: You cannot transfer a student to another department or section."));
    }

    @Test
    @DisplayName("Duplicate Hackathon creation warns the admin and allows creation upon confirmation")
    void testDuplicateHackathonCreationWarningAndConfirmation() {
        // Admin A creates a Hackathon
        authenticateUser(adminEceA);

        HackathonCreateRequest firstReq = HackathonCreateRequest.builder()
                .name("Grand AI Hackathon 2026")
                .organizedBy("IEEE Student Branch")
                .registrationLink("https://ieee.org/hack")
                .registrationStart(LocalDate.now())
                .registrationEnd(LocalDate.now().plusDays(10))
                .maximumTeamSize(5)
                .mode(HackathonMode.ONLINE)
                .description("National level hackathon")
                .confirmDuplicate(false)
                .build();

        HackathonResponse firstCreated = hackathonService.createHackathon(firstReq);
        assertNotNull(firstCreated);
        assertEquals("Grand AI Hackathon 2026", firstCreated.getName());

        // Admin B tries to create a hackathon with the same name without confirmDuplicate
        authenticateUser(adminCseB);

        HackathonCreateRequest dupReq = HackathonCreateRequest.builder()
                .name("Grand AI Hackathon 2026")
                .organizedBy("CSE Association")
                .registrationLink("https://cse.college.edu/hack")
                .registrationStart(LocalDate.now())
                .registrationEnd(LocalDate.now().plusDays(14))
                .maximumTeamSize(4)
                .mode(HackathonMode.OFFLINE)
                .venue("Campus Hall B")
                .description("Intra-college hackathon edition")
                .confirmDuplicate(false)
                .build();

        DuplicateHackathonException dupEx = assertThrows(DuplicateHackathonException.class, () ->
                hackathonService.createHackathon(dupReq)
        );

        // Verify exact wording required by prompt
        assertEquals("This hackathon is already created by 'another admin' do you want to create it?", dupEx.getMessage());

        // Now Admin B confirms duplication (clicked "Yes")
        dupReq.setConfirmDuplicate(true);
        HackathonResponse secondCreated = hackathonService.createHackathon(dupReq);
        assertNotNull(secondCreated);
        assertEquals("Grand AI Hackathon 2026", secondCreated.getName());
        assertNotEquals(firstCreated.getId(), secondCreated.getId());
    }
}
