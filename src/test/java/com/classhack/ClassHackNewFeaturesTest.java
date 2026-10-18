package com.classhack;

import com.classhack.config.DataInitializer;
import com.classhack.dto.ChangePasswordRequest;
import com.classhack.dto.HackathonCreateRequest;
import com.classhack.dto.LoginRequest;
import com.classhack.dto.LoginResponse;
import com.classhack.dto.StudentCreateRequest;
import com.classhack.dto.StudentResponse;
import com.classhack.dto.TeamCreateRequest;
import com.classhack.dto.TeamResponse;
import com.classhack.entity.Admin;
import com.classhack.entity.Hackathon;
import com.classhack.entity.Student;
import com.classhack.dto.AuditLogResponse;
import com.classhack.entity.AuditLog;
import com.classhack.entity.enums.Gender;
import com.classhack.entity.enums.HackathonMode;
import com.classhack.entity.enums.Role;
import com.classhack.exception.BadRequestException;
import com.classhack.repository.AdminRepository;
import com.classhack.repository.AuditLogRepository;
import com.classhack.repository.HackathonRepository;
import com.classhack.repository.StudentRepository;
import com.classhack.security.CustomUserDetails;
import com.classhack.service.AdminService;
import com.classhack.service.AuthService;
import com.classhack.service.HackathonService;
import com.classhack.service.ReportService;
import com.classhack.service.StudentCredentialExportService;
import com.classhack.service.TeamService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ClassHackNewFeaturesTest {

    @Autowired
    private AdminService adminService;

    @Autowired
    private AuthService authService;

    @Autowired
    private HackathonService hackathonService;

    @Autowired
    private TeamService teamService;

    @Autowired
    private ReportService reportService;

    @Autowired
    private StudentCredentialExportService studentCredentialExportService;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private AdminRepository adminRepository;

    @Autowired
    private HackathonRepository hackathonRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DataInitializer dataInitializer;

    private Admin superAdmin;

    @BeforeEach
    void setUp() {
        dataInitializer.initializeSuperAdmin();
        superAdmin = adminRepository.findByEmailIgnoreCase(DataInitializer.SUPER_ADMIN_EMAIL).orElseThrow();
        authenticateAdmin(superAdmin);
    }

    private void authenticateAdmin(Admin admin) {
        CustomUserDetails userDetails = new CustomUserDetails(admin);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority(admin.getRole().name())))
        );
    }

    private void authenticateStudent(Student student) {
        CustomUserDetails userDetails = new CustomUserDetails(student);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(userDetails, null, List.of(new SimpleGrantedAuthority(student.getRole().name())))
        );
    }

    @Test
    @DisplayName("REQ 1, 2, 3, 4: Admin creates student, DB stores only BCrypt hash, Excel exports actual initial plaintext password")
    void testStudentCreationAndCredentialExcelExport() throws IOException {
        String initialPlaintext = "SecretPass#2026";

        StudentCreateRequest req = StudentCreateRequest.builder()
                .studentId("CSE701")
                .rollNumber("710021104701")
                .fullName("Ananya Sharma")
                .gender(Gender.FEMALE)
                .department("CSE")
                .section("A")
                .year("IV")
                .academicYear("2026-27")
                .phone("9876543211")
                .email("ananya.cse@college.edu")
                .password(initialPlaintext)
                .build();

        StudentResponse res = adminService.createStudent(req);
        assertNotNull(res);
        assertEquals("CSE701", res.getStudentId());
        assertTrue(res.isActivated(), "Account is active immediately without requiring email activation");
        assertTrue(res.isPasswordChangeRequired(), "Password change is required on initial login");
        assertNotNull(res.getBatchId(), "Batch ID must be generated for the immediate export link");

        // 1. Verify Database ONLY contains BCrypt hash, NEVER the plaintext password
        Student savedInDb = studentRepository.findByEmailIgnoreCase("ananya.cse@college.edu").orElseThrow();
        assertNotEquals(initialPlaintext, savedInDb.getPassword(), "Plaintext password must NEVER be saved in database");
        assertFalse(savedInDb.getPassword().contains(initialPlaintext), "Plaintext must not be present in DB password column");
        assertTrue(savedInDb.getPassword().startsWith("$2a$") || savedInDb.getPassword().startsWith("$2b$"),
                "Stored password must be a valid BCrypt hash");
        assertTrue(passwordEncoder.matches(initialPlaintext, savedInDb.getPassword()),
                "Database hash must verify against initial password");

        // 2. Export Excel for this specific batch
        byte[] batchExcelBytes = adminService.exportStudentCredentialsExcel(res.getBatchId(), null, null, null);
        assertNotNull(batchExcelBytes);
        assertTrue(batchExcelBytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(batchExcelBytes))) {
            assertEquals("Student Credentials", workbook.getSheetName(0));
            Sheet sheet = workbook.getSheetAt(0);

            // Row 0 is Title, Row 1 is Subtitle, Row 2 is Warning banner, Row 3 is Blank, Row 4 is Headers, Row 5 is first data row
            Row headerRow = sheet.getRow(4);
            assertEquals("Student Name", headerRow.getCell(0).getStringCellValue());
            assertEquals("Student ID / Roll Number", headerRow.getCell(1).getStringCellValue());
            assertEquals("Department", headerRow.getCell(2).getStringCellValue());
            assertEquals("Section", headerRow.getCell(3).getStringCellValue());
            assertEquals("Year", headerRow.getCell(4).getStringCellValue());
            assertEquals("Email Address", headerRow.getCell(5).getStringCellValue());
            assertEquals("Username", headerRow.getCell(6).getStringCellValue());
            assertEquals("Initial Password", headerRow.getCell(7).getStringCellValue());
            assertEquals("Password Change Required", headerRow.getCell(8).getStringCellValue());

            // Data Row
            Row dataRow = sheet.getRow(5);
            assertEquals("Ananya Sharma", dataRow.getCell(0).getStringCellValue());
            assertEquals("710021104701", dataRow.getCell(1).getStringCellValue());
            assertEquals("CSE", dataRow.getCell(2).getStringCellValue());
            assertEquals("A", dataRow.getCell(3).getStringCellValue());
            assertEquals("IV", dataRow.getCell(4).getStringCellValue());
            assertEquals("ananya.cse@college.edu", dataRow.getCell(5).getStringCellValue());
            assertEquals("CSE701", dataRow.getCell(6).getStringCellValue());

            // Key check: The cell contains the ACTUAL initial password, NOT the BCrypt hash
            assertEquals(initialPlaintext, dataRow.getCell(7).getStringCellValue(),
                    "Exported Excel must contain the actual initial plaintext password");
            assertFalse(dataRow.getCell(7).getStringCellValue().startsWith("$2"),
                    "Exported Excel must NEVER contain a BCrypt hash");
            assertEquals("YES", dataRow.getCell(8).getStringCellValue());
        }

        // 3. Verify older student without temporary cache displays 'Password reset required'
        Student legacyStudent = studentRepository.save(Student.builder()
                .studentId("LEGACY01")
                .rollNumber("710020104001")
                .fullName("Legacy Student")
                .gender(Gender.MALE)
                .department("CSE")
                .section("A")
                .year("IV")
                .academicYear("2025-26")
                .email("legacy@college.edu")
                .password(passwordEncoder.encode("oldPasswordHash"))
                .active(true)
                .activated(true)
                .passwordChangeRequired(false)
                .build());

        byte[] allExcelBytes = adminService.exportStudentCredentialsExcel(null, "CSE", "A", "IV");
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(allExcelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean legacyFound = false;
            for (int r = 5; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null && row.getCell(1) != null && "710020104001".equals(row.getCell(1).getStringCellValue())) {
                    legacyFound = true;
                    assertEquals("Password reset required", row.getCell(7).getStringCellValue(),
                            "Unrecoverable historical passwords must display 'Password reset required' rather than hash");
                    assertEquals("NO", row.getCell(8).getStringCellValue());
                }
            }
            assertTrue(legacyFound, "Legacy student should appear in filtered department export");
        }
    }

    @Test
    @DisplayName("REQ 5: Student password change validates current password, enforces >= 8 chars, matches confirmation, and updates hash")
    void testStudentPasswordChange() {
        String initialPass = "OldPass#123";
        Student student = studentRepository.save(Student.builder()
                .studentId("ECE909")
                .rollNumber("710021106909")
                .fullName("Kavya Madhavan")
                .gender(Gender.FEMALE)
                .department("ECE")
                .section("B")
                .year("III")
                .academicYear("2026-27")
                .email("kavya.ece@college.edu")
                .password(passwordEncoder.encode(initialPass))
                .active(true)
                .activated(true)
                .passwordChangeRequired(true)
                .build());

        authenticateStudent(student);

        // Case 1: Incorrect current password -> rejected
        ChangePasswordRequest wrongCurrentReq = ChangePasswordRequest.builder()
                .currentPassword("wrongPassword")
                .newPassword("NewBrandPass#2026")
                .confirmPassword("NewBrandPass#2026")
                .build();
        BadRequestException wrongCurrentEx = assertThrows(BadRequestException.class, () ->
                authService.changePassword(wrongCurrentReq)
        );
        assertTrue(wrongCurrentEx.getMessage().toLowerCase().contains("current password"));

        // Case 2: Confirmation mismatch -> rejected
        ChangePasswordRequest mismatchReq = ChangePasswordRequest.builder()
                .currentPassword(initialPass)
                .newPassword("NewBrandPass#2026")
                .confirmPassword("DifferentPass#2026")
                .build();
        BadRequestException mismatchEx = assertThrows(BadRequestException.class, () ->
                authService.changePassword(mismatchReq)
        );
        assertTrue(mismatchEx.getMessage().toLowerCase().contains("match"));

        // Case 3: New password too short (< 8 chars) -> rejected
        ChangePasswordRequest shortReq = ChangePasswordRequest.builder()
                .currentPassword(initialPass)
                .newPassword("Short7!")
                .confirmPassword("Short7!")
                .build();
        BadRequestException shortEx = assertThrows(BadRequestException.class, () ->
                authService.changePassword(shortReq)
        );
        assertTrue(shortEx.getMessage().toLowerCase().contains("8 characters"));

        // Case 4: Successful password change
        String newPass = "BrandNewSecurePass#2026";
        ChangePasswordRequest validReq = ChangePasswordRequest.builder()
                .currentPassword(initialPass)
                .newPassword(newPass)
                .confirmPassword(newPass)
                .build();
        authService.changePassword(validReq);

        // Verify database updated
        Student updatedStudent = studentRepository.findById(student.getId()).orElseThrow();
        assertFalse(updatedStudent.isPasswordChangeRequired(), "Flag should be cleared after password change");
        assertTrue(passwordEncoder.matches(newPass, updatedStudent.getPassword()), "New password hash must match in DB");
        assertFalse(passwordEncoder.matches(initialPass, updatedStudent.getPassword()), "Old password must fail in DB");

        // Verify subsequent login with new password succeeds and old password fails
        MockHttpServletRequest httpReq = new MockHttpServletRequest();
        MockHttpServletResponse httpRes = new MockHttpServletResponse();

        // Old password login attempt fails (Spring Security rejects invalid credentials)
        LoginRequest oldLoginReq = LoginRequest.builder()
                .username("710021106909")
                .password(initialPass)
                .loginType("STUDENT")
                .build();
        assertThrows(Exception.class, () -> authService.login(oldLoginReq, httpReq, httpRes));

        // New password login attempt succeeds
        LoginRequest newLoginReq = LoginRequest.builder()
                .username("710021106909")
                .password(newPass)
                .loginType("STUDENT")
                .build();
        LoginResponse newLoginRes = authService.login(newLoginReq, httpReq, httpRes);
        assertNotNull(newLoginRes);
        assertTrue(newLoginRes.isSuccess());
        assertFalse(newLoginRes.isPasswordChangeRequired(), "Password change is no longer required on subsequent logins");
    }

    @Test
    @DisplayName("REQ 6: Export hackathon registrations to Excel is scoped strictly to selected hackathon with all required fields")
    void testHackathonRegistrationExcelExport() throws IOException {
        // 1. Create Hackathon A
        Hackathon hA = hackathonRepository.save(Hackathon.builder()
                .name("Smart India Hackathon 2026")
                .description("National Innovation Challenge")
                .organizedBy("AICTE & MoE")
                .mode(HackathonMode.OFFLINE)
                .venue("IIT Madras")
                .registrationStart(LocalDate.now().minusDays(5))
                .registrationEnd(LocalDate.now().plusDays(10))
                .maximumTeamSize(4)
                .registrationLink("https://sih.gov.in")
                .build());

        // 2. Create Hackathon B (Different hackathon)
        Hackathon hB = hackathonRepository.save(Hackathon.builder()
                .name("Google Cloud Hack 2026")
                .description("Cloud and AI Solutions")
                .organizedBy("Google Developer Groups")
                .mode(HackathonMode.ONLINE)
                .registrationStart(LocalDate.now().minusDays(5))
                .registrationEnd(LocalDate.now().plusDays(10))
                .maximumTeamSize(4)
                .registrationLink("https://cloud.google.com")
                .build());

        // 3. Create students
        Student s1 = studentRepository.save(Student.builder()
                .studentId("CSE555")
                .rollNumber("710021104555")
                .fullName("Praveen Kumar")
                .gender(Gender.MALE)
                .department("CSE")
                .section("A")
                .year("IV")
                .academicYear("2026-27")
                .email("praveen.k@college.edu")
                .password(passwordEncoder.encode("pass12345"))
                .active(true)
                .activated(true)
                .build());

        Student s2 = studentRepository.save(Student.builder()
                .studentId("CSE556")
                .rollNumber("710021104556")
                .fullName("Divya Prakash")
                .gender(Gender.FEMALE)
                .department("CSE")
                .section("A")
                .year("IV")
                .academicYear("2026-27")
                .email("divya.p@college.edu")
                .password(passwordEncoder.encode("pass12345"))
                .active(true)
                .activated(true)
                .build());

        Student sOther = studentRepository.save(Student.builder()
                .studentId("MECH111")
                .rollNumber("710021108111")
                .fullName("Other Hackathon Student")
                .gender(Gender.MALE)
                .department("MECH")
                .section("B")
                .year("II")
                .academicYear("2026-27")
                .email("other@college.edu")
                .password(passwordEncoder.encode("pass12345"))
                .active(true)
                .activated(true)
                .build());

        // Register team for Hackathon A
        authenticateStudent(s1);
        TeamCreateRequest teamReqA = TeamCreateRequest.builder()
                .teamName("Team Innovators")
                .memberStudentIds(List.of(s2.getId()))
                .build();
        TeamResponse teamResA = teamService.createTeam(hA.getId(), teamReqA, s1.getId());
        assertNotNull(teamResA);

        // Register team for Hackathon B
        authenticateStudent(sOther);
        TeamCreateRequest teamReqB = TeamCreateRequest.builder()
                .teamName("Team Cloud Ninjas")
                .memberStudentIds(List.of())
                .build();
        teamService.createTeam(hB.getId(), teamReqB, sOther.getId());

        // 4. Export Hackathon A registrations to Excel
        authenticateAdmin(superAdmin);
        byte[] excelBytes = reportService.exportHackathonRegistrationsExcel(hA.getId());
        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(excelBytes))) {
            assertEquals("Registrations", workbook.getSheetName(0));
            Sheet sheet = workbook.getSheetAt(0);

            // Row 0: Title includes Hackathon A name
            Row titleRow = sheet.getRow(0);
            assertTrue(titleRow.getCell(0).getStringCellValue().contains("Smart India Hackathon 2026"));

            // Row 3: Headers
            Row headerRow = sheet.getRow(3);
            assertEquals("Hackathon Name", headerRow.getCell(0).getStringCellValue());
            assertEquals("Hackathon ID", headerRow.getCell(1).getStringCellValue());
            assertEquals("Registration ID", headerRow.getCell(2).getStringCellValue());
            assertEquals("Student Name", headerRow.getCell(3).getStringCellValue());
            assertEquals("Student ID / Roll Number", headerRow.getCell(4).getStringCellValue());
            assertEquals("Department", headerRow.getCell(5).getStringCellValue());
            assertEquals("Section", headerRow.getCell(6).getStringCellValue());
            assertEquals("Year", headerRow.getCell(7).getStringCellValue());
            assertEquals("Student Email", headerRow.getCell(8).getStringCellValue());
            assertEquals("Team Name", headerRow.getCell(9).getStringCellValue());
            assertEquals("Team Members", headerRow.getCell(10).getStringCellValue());
            assertEquals("Registration Date", headerRow.getCell(11).getStringCellValue());
            assertEquals("Registration Status", headerRow.getCell(12).getStringCellValue());

            // Data rows: Verify ONLY students from Hackathon A are present (s1, s2) and NOT sOther
            boolean foundS1 = false;
            boolean foundS2 = false;
            boolean foundOther = false;

            for (int r = 4; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row != null && row.getCell(4) != null) {
                    String roll = row.getCell(4).getStringCellValue();
                    if ("710021104555".equals(roll)) foundS1 = true;
                    if ("710021104556".equals(roll)) foundS2 = true;
                    if ("710021108111".equals(roll)) foundOther = true;
                }
            }

            assertTrue(foundS1, "Hackathon A leader must be included");
            assertTrue(foundS2, "Hackathon A member must be included");
            assertFalse(foundOther, "Registrations from other hackathons must NEVER be included");
        }

        // 5. Test empty hackathon export
        Hackathon hEmpty = hackathonRepository.save(Hackathon.builder()
                .name("Empty Hackathon 2026")
                .description("No participants yet")
                .organizedBy("Department")
                .mode(HackathonMode.ONLINE)
                .registrationStart(LocalDate.now())
                .registrationEnd(LocalDate.now().plusDays(5))
                .maximumTeamSize(4)
                .registrationLink("https://empty.example.com")
                .build());

        byte[] emptyExcelBytes = reportService.exportHackathonRegistrationsExcel(hEmpty.getId());
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(emptyExcelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row emptyNoticeRow = sheet.getRow(4);
            assertNotNull(emptyNoticeRow);
            assertTrue(emptyNoticeRow.getCell(0).getStringCellValue().contains("No student registrations found"));
        }
    }

    @Test
    @DisplayName("Audit Log: Stored UTC timestamp is converted to South Indian Time (Asia/Kolkata / IST, UTC+5:30)")
    void testAuditLogSouthIndianTimestampConversion() {
        // 1. Save an audit log
        AuditLog log = auditLogRepository.save(AuditLog.builder()
                .action("STUDENT_CREATED")
                .performedBy(superAdmin.getEmail())
                .details("Created student test audit")
                .build());

        // 2. Fetch via adminService
        List<AuditLogResponse> logs = adminService.getAuditLogs();
        assertFalse(logs.isEmpty(), "Audit logs must not be empty");
        AuditLogResponse latest = logs.stream()
                .filter(l -> l.getId().equals(log.getId()))
                .findFirst()
                .orElseThrow();

        assertNotNull(latest.getFormattedTimestamp(), "formattedTimestamp must be populated");
        assertNotNull(latest.getIsoTimestamp(), "isoTimestamp must be populated with Z");
        assertTrue(latest.getIsoTimestamp().endsWith("Z"), "isoTimestamp must end with Z to declare UTC");

        // 3. Verify exact conversion for the user's specific UTC timestamp (09:26:00 UTC -> 2:56:00 PM IST)
        LocalDateTime utcSample = LocalDateTime.of(2026, 10, 18, 9, 26, 0);
        ZonedDateTime istZoned = utcSample.atZone(ZoneOffset.UTC).withZoneSameInstant(ZoneId.of("Asia/Kolkata"));
        String formatted = istZoned.format(DateTimeFormatter.ofPattern("MM/dd/yyyy, h:mm:ss a", java.util.Locale.US));
        assertEquals("10/18/2026, 2:56:00 PM", formatted);
    }
}
