package com.classhack.service;

import com.classhack.config.DataInitializer;
import com.classhack.dto.*;
import com.classhack.entity.Admin;
import com.classhack.entity.AuditLog;
import com.classhack.entity.Hackathon;
import com.classhack.entity.Student;
import com.classhack.entity.Team;
import com.classhack.entity.TeamMember;
import com.classhack.entity.enums.HackathonStatus;
import com.classhack.entity.enums.Role;
import com.classhack.entity.enums.TeamStatus;
import com.classhack.exception.BadRequestException;
import com.classhack.exception.DuplicateResourceException;
import com.classhack.exception.ResourceNotFoundException;
import com.classhack.repository.*;
import com.classhack.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final StudentRepository studentRepository;
    private final AdminRepository adminRepository;
    private final HackathonRepository hackathonRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final StudentCredentialExportService studentCredentialExportService;

    private boolean isSuperAdmin(CustomUserDetails admin) {
        if (admin == null) return false;
        return DataInitializer.SUPER_ADMIN_EMAIL.equalsIgnoreCase(admin.getEmail()) ||
                "CENTRAL".equalsIgnoreCase(admin.getDepartment()) ||
                "ALL".equalsIgnoreCase(admin.getDepartment());
    }

    private void checkStudentDepartmentSectionAccess(CustomUserDetails admin, Student student, String action) {
        if (admin == null) {
            throw new BadRequestException("User not authenticated.");
        }
        if (isSuperAdmin(admin)) {
            return; // Super admin has global oversight
        }
        if (!student.getDepartment().equalsIgnoreCase(admin.getDepartment()) ||
            !student.getSection().equalsIgnoreCase(admin.getSection())) {
            throw new BadRequestException("Access Denied: You cannot " + action + " students of another department (" 
                    + student.getDepartment() + ") or section (" + student.getSection() + "). You are assigned to " 
                    + admin.getDepartment() + "-" + admin.getSection() + ".");
        }
    }

    @Transactional
    public StudentResponse createStudent(StudentCreateRequest req) {
        CustomUserDetails admin = authService.getCurrentUser();
        if (admin == null) {
            throw new BadRequestException("User not authenticated.");
        }

        // Enforce department and section restrictions for regular admins
        if (!isSuperAdmin(admin)) {
            if (!admin.getDepartment().equalsIgnoreCase(req.getDepartment().trim()) ||
                !admin.getSection().equalsIgnoreCase(req.getSection().trim())) {
                throw new BadRequestException("Access Denied: You can only add students of your assigned department (" 
                        + admin.getDepartment() + ") and section (" + admin.getSection() + ").");
            }
        }

        String studentId = req.getStudentId().trim().toUpperCase();
        String email = req.getEmail().trim().toLowerCase();

        if (studentRepository.existsByStudentIdIgnoreCase(studentId)) {
            throw new DuplicateResourceException("Student ID / Roll Number '" + studentId + "' already exists.");
        }
        if (studentRepository.existsByEmailIgnoreCase(email) || adminRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Email '" + email + "' is already registered in the system.");
        }

        String rollNumber = (req.getRollNumber() != null && !req.getRollNumber().isBlank())
                ? req.getRollNumber().trim()
                : studentId;

        String rawPassword = (req.getPassword() != null && !req.getPassword().isBlank())
                ? req.getPassword().trim()
                : generateSecurePassword();

        Student student = Student.builder()
                .studentId(studentId)
                .rollNumber(rollNumber)
                .fullName(req.getFullName().trim())
                .gender(req.getGender())
                .department(req.getDepartment().trim().toUpperCase())
                .section(req.getSection().trim().toUpperCase())
                .year(req.getYear().trim())
                .academicYear(req.getAcademicYear().trim())
                .phone(req.getPhone() != null ? req.getPhone().trim() : null)
                .email(email)
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.ROLE_STUDENT)
                .active(true)
                .activated(true)
                .passwordChangeRequired(true)
                .build();

        Student saved = studentRepository.save(student);

        // Retain initial credentials in memory temporarily for secure authorized Excel export
        String batchId = UUID.randomUUID().toString();
        studentCredentialExportService.recordInitialCredential(saved.getStudentId(), rawPassword, batchId, admin.getId());

        String adminName = admin.getEmail();
        auditLogRepository.save(AuditLog.builder()
                .action("STUDENT_CREATED")
                .performedBy(adminName)
                .details("Created student: " + saved.getFullName() + " (" + saved.getStudentId() + ", " + saved.getDepartment() + "-" + saved.getSection() + ")")
                .build());

        StudentResponse response = mapToStudentResponse(saved);
        response.setBatchId(batchId);
        response.setPasswordChangeRequired(true);
        response.setEmailSent(false);
        response.setEmailMessage("Email sending disabled. Initial credentials ready for Excel export.");
        return response;
    }

    private String generateSecurePassword() {
        return "Class@" + (1000 + new java.security.SecureRandom().nextInt(9000));
    }

    @Transactional
    public StudentResponse updateStudent(Long id, StudentUpdateRequest req) {
        CustomUserDetails admin = authService.getCurrentUser();
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + id));

        checkStudentDepartmentSectionAccess(admin, student, "modify");

        if (!isSuperAdmin(admin)) {
            if (!admin.getDepartment().equalsIgnoreCase(req.getDepartment().trim()) ||
                !admin.getSection().equalsIgnoreCase(req.getSection().trim())) {
                throw new BadRequestException("Access Denied: You cannot transfer a student to another department or section.");
            }
        }

        String email = req.getEmail().trim().toLowerCase();
        if (!student.getEmail().equalsIgnoreCase(email)) {
            if (studentRepository.existsByEmailIgnoreCase(email) || adminRepository.existsByEmailIgnoreCase(email)) {
                throw new DuplicateResourceException("Email '" + email + "' is already used by another account.");
            }
            student.setEmail(email);
        }

        student.setFullName(req.getFullName().trim());
        student.setGender(req.getGender());
        student.setDepartment(req.getDepartment().trim().toUpperCase());
        student.setSection(req.getSection().trim().toUpperCase());
        student.setYear(req.getYear().trim());
        student.setAcademicYear(req.getAcademicYear().trim());
        student.setPhone(req.getPhone() != null ? req.getPhone().trim() : null);

        if (req.getActive() != null) {
            student.setActive(req.getActive());
        }

        if (req.getNewPassword() != null && !req.getNewPassword().isBlank()) {
            if (req.getNewPassword().length() < 6) {
                throw new BadRequestException("Password must be at least 6 characters.");
            }
            student.setPassword(passwordEncoder.encode(req.getNewPassword()));
        }

        Student updated = studentRepository.save(student);

        String adminName = admin != null ? admin.getEmail() : "Admin";
        auditLogRepository.save(AuditLog.builder()
                .action("STUDENT_UPDATED")
                .performedBy(adminName)
                .details("Updated student: " + updated.getFullName() + " (" + updated.getStudentId() + ")")
                .build());

        return mapToStudentResponse(updated);
    }

    @Transactional
    public void toggleStudentStatus(Long id) {
        CustomUserDetails admin = authService.getCurrentUser();
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + id));

        checkStudentDepartmentSectionAccess(admin, student, "change status of");

        student.setActive(!student.isActive());
        studentRepository.save(student);

        String adminName = admin != null ? admin.getEmail() : "Admin";
        auditLogRepository.save(AuditLog.builder()
                .action(student.isActive() ? "STUDENT_ACTIVATED" : "STUDENT_DEACTIVATED")
                .performedBy(adminName)
                .details((student.isActive() ? "Activated" : "Deactivated") + " student: " + student.getStudentId())
                .build());
    }

    @Transactional
    public void resetStudentPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.trim().length() < 6) {
            throw new BadRequestException("New password must be at least 6 characters long.");
        }
        CustomUserDetails admin = authService.getCurrentUser();
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + id));

        checkStudentDepartmentSectionAccess(admin, student, "reset password for");

        student.setPassword(passwordEncoder.encode(newPassword.trim()));
        studentRepository.save(student);

        String adminName = admin != null ? admin.getEmail() : "Admin";
        auditLogRepository.save(AuditLog.builder()
                .action("STUDENT_PASSWORD_RESET")
                .performedBy(adminName)
                .details("Reset password for student: " + student.getStudentId())
                .build());
    }

    public List<StudentResponse> getAllStudents() {
        CustomUserDetails admin = authService.getCurrentUser();
        if (admin == null) {
            throw new BadRequestException("User not authenticated.");
        }
        List<Student> students = isSuperAdmin(admin)
                ? studentRepository.findAllByOrderByCreatedAtDesc()
                : studentRepository.findByDepartmentIgnoreCaseAndSectionIgnoreCaseOrderByCreatedAtDesc(
                        admin.getDepartment(), admin.getSection());

        return students.stream()
                .map(this::mapToStudentResponse)
                .collect(Collectors.toList());
    }

    public StudentResponse getStudentById(Long id) {
        CustomUserDetails admin = authService.getCurrentUser();
        Student student = studentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + id));

        checkStudentDepartmentSectionAccess(admin, student, "view");
        return mapToStudentResponse(student);
    }

    public List<StudentResponse> searchStudents(String keyword) {
        CustomUserDetails admin = authService.getCurrentUser();
        if (admin == null) {
            throw new BadRequestException("User not authenticated.");
        }
        List<Student> students = isSuperAdmin(admin)
                ? studentRepository.searchStudents(keyword)
                : studentRepository.searchStudentsByDeptAndSection(keyword, admin.getDepartment(), admin.getSection());

        return students.stream()
                .map(this::mapToStudentResponse)
                .collect(Collectors.toList());
    }

    public StudentParticipationHistoryResponse getStudentParticipationHistory(Long studentId) {
        CustomUserDetails admin = authService.getCurrentUser();
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        checkStudentDepartmentSectionAccess(admin, student, "view participation history of");

        List<TeamMember> memberships = teamMemberRepository.findByStudentId(student.getId());

        List<StudentParticipationHistoryResponse.ParticipationItem> items = new ArrayList<>();
        long wins = 0;
        long finals = 0;
        long shortlisted = 0;

        for (TeamMember m : memberships) {
            Team team = m.getTeam();
            if (team.isDeleted()) {
                continue;
            }
            Hackathon hackathon = team.getHackathon();

            String badge = switch (team.getStatus()) {
                case WINNER -> "🏆 Winner";
                case FINALIST -> "🥈 Finalist";
                case NEXT_ROUND -> "🚀 Next Round";
                case SHORTLISTED -> "⭐ Shortlisted";
                case ELIMINATED -> "❌ Eliminated";
                case WITHDRAWN -> "⛔ Withdrawn";
                default -> "📝 Registered";
            };

            if (team.getStatus() == TeamStatus.WINNER) wins++;
            else if (team.getStatus() == TeamStatus.FINALIST) finals++;
            else if (team.getStatus() == TeamStatus.SHORTLISTED || team.getStatus() == TeamStatus.NEXT_ROUND) shortlisted++;

            items.add(StudentParticipationHistoryResponse.ParticipationItem.builder()
                    .hackathonId(hackathon.getId())
                    .hackathonName(hackathon.getName())
                    .organizedBy(hackathon.getOrganizedBy())
                    .mode(hackathon.getMode().name())
                    .date(hackathon.getRegistrationStart())
                    .teamId(team.getId())
                    .teamName(team.getTeamName())
                    .role(m.getRole())
                    .status(team.getStatus())
                    .achievementBadge(badge)
                    .build());
        }

        return StudentParticipationHistoryResponse.builder()
                .studentDbId(student.getId())
                .studentId(student.getStudentId())
                .fullName(student.getFullName())
                .department(student.getDepartment())
                .section(student.getSection())
                .year(student.getYear())
                .academicYear(student.getAcademicYear())
                .email(student.getEmail())
                .phone(student.getPhone())
                .totalHackathons(items.size())
                .totalWins(wins)
                .totalFinals(finals)
                .totalShortlisted(shortlisted)
                .participations(items)
                .build();
    }

    public DashboardStatsResponse getDashboardStats() {
        CustomUserDetails admin = authService.getCurrentUser();
        long totalStudents = (admin != null && !isSuperAdmin(admin))
                ? studentRepository.countByDepartmentIgnoreCaseAndSectionIgnoreCaseAndActiveTrue(admin.getDepartment(), admin.getSection())
                : studentRepository.countByActiveTrue();
        List<Hackathon> allHackathons = hackathonRepository.findByDeletedFalseOrderByRegistrationStartDesc();

        long totalHackathons = allHackathons.size();
        long openHackathons = allHackathons.stream().filter(Hackathon::isRegistrationOpen).count();
        long closedHackathons = allHackathons.stream()
                .filter(h -> h.getComputedStatus() == HackathonStatus.REGISTRATION_CLOSED || h.getComputedStatus() == HackathonStatus.COMPLETED)
                .count();

        long totalTeams = teamRepository.countByDeletedFalse();
        long totalParticipants = teamMemberRepository.countDistinctParticipants();
        long shortlistedTeams = teamRepository.countByStatusAndDeletedFalse(TeamStatus.SHORTLISTED)
                + teamRepository.countByStatusAndDeletedFalse(TeamStatus.NEXT_ROUND);
        long finalists = teamRepository.countByStatusAndDeletedFalse(TeamStatus.FINALIST);
        long winners = teamRepository.countByStatusAndDeletedFalse(TeamStatus.WINNER);

        return DashboardStatsResponse.builder()
                .totalStudents(totalStudents)
                .totalHackathons(totalHackathons)
                .openHackathons(openHackathons)
                .closedHackathons(closedHackathons)
                .totalTeams(totalTeams)
                .totalParticipants(totalParticipants)
                .shortlistedTeams(shortlistedTeams)
                .finalists(finalists)
                .winners(winners)
                .build();
    }

    public List<AuditLogResponse> getAuditLogs() {
        return auditLogRepository.findTop100ByOrderByTimestampDesc().stream()
                .map(a -> AuditLogResponse.builder()
                        .id(a.getId())
                        .action(a.getAction())
                        .performedBy(a.getPerformedBy())
                        .details(a.getDetails())
                        .timestamp(a.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    private StudentResponse mapToStudentResponse(Student student) {
        List<TeamMember> memberships = teamMemberRepository.findByStudentId(student.getId());
        long wins = 0;
        long finals = 0;
        long shortlisted = 0;
        long count = 0;

        for (TeamMember m : memberships) {
            if (!m.getTeam().isDeleted()) {
                count++;
                if (m.getTeam().getStatus() == TeamStatus.WINNER) wins++;
                else if (m.getTeam().getStatus() == TeamStatus.FINALIST) finals++;
                else if (m.getTeam().getStatus() == TeamStatus.SHORTLISTED || m.getTeam().getStatus() == TeamStatus.NEXT_ROUND) shortlisted++;
            }
        }

        return StudentResponse.builder()
                .id(student.getId())
                .studentId(student.getStudentId())
                .fullName(student.getFullName())
                .gender(student.getGender())
                .department(student.getDepartment())
                .section(student.getSection())
                .year(student.getYear())
                .academicYear(student.getAcademicYear())
                .phone(student.getPhone())
                .email(student.getEmail())
                .rollNumber(student.getRollNumber())
                .active(student.isActive())
                .activated(student.isActivated())
                .passwordChangeRequired(student.isPasswordChangeRequired())
                .createdAt(student.getCreatedAt())
                .totalHackathons(count)
                .totalWins(wins)
                .totalFinals(finals)
                .totalShortlisted(shortlisted)
                .build();
    }

    public List<AdminResponse> getAllAdmins() {
        CustomUserDetails currentUser = authService.getCurrentUser();
        if (currentUser == null || !DataInitializer.SUPER_ADMIN_EMAIL.equalsIgnoreCase(currentUser.getEmail())) {
            throw new BadRequestException("Access Denied: Only the Super Administrator (" + DataInitializer.SUPER_ADMIN_EMAIL + ") can view administrator accounts.");
        }

        return adminRepository.findAll().stream()
                .map(this::mapToAdminResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteAdmin(Long id) {
        CustomUserDetails currentUser = authService.getCurrentUser();
        if (currentUser == null || !DataInitializer.SUPER_ADMIN_EMAIL.equalsIgnoreCase(currentUser.getEmail())) {
            throw new BadRequestException("Access Denied: Only the Super Administrator (" + DataInitializer.SUPER_ADMIN_EMAIL + ") can delete administrator accounts.");
        }

        Admin admin = adminRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Administrator not found with ID: " + id));

        if (DataInitializer.SUPER_ADMIN_EMAIL.equalsIgnoreCase(admin.getEmail())) {
            throw new BadRequestException("Cannot delete the Super Administrator account.");
        }

        adminRepository.delete(admin);

        auditLogRepository.save(AuditLog.builder()
                .action("ADMIN_DELETED")
                .performedBy(currentUser.getEmail())
                .details("Super Admin deleted administrator: " + admin.getFullName() + " (" + admin.getEmail() + ", " + admin.getPosition() + ")")
                .build());
    }

    private AdminResponse mapToAdminResponse(Admin admin) {
        boolean isSuperAdmin = DataInitializer.SUPER_ADMIN_EMAIL.equalsIgnoreCase(admin.getEmail());
        return AdminResponse.builder()
                .id(admin.getId())
                .fullName(admin.getFullName())
                .email(admin.getEmail())
                .phone(admin.getPhone())
                .department(admin.getDepartment())
                .section(admin.getSection())
                .position(admin.getPosition())
                .role(admin.getRole().name())
                .active(admin.isActive())
                .activated(admin.isActivated())
                .isSuperAdmin(isSuperAdmin)
                .createdAt(admin.getCreatedAt())
                .build();
    }

    public byte[] exportStudentCredentialsExcel(String batchId, String department, String section, String year) throws IOException {
        CustomUserDetails admin = authService.getCurrentUser();
        if (admin == null) {
            throw new BadRequestException("User not authenticated.");
        }

        List<Student> students;
        String subtitle;

        if (batchId != null && !batchId.isBlank()) {
            List<String> studentIds = studentCredentialExportService.getStudentIdsForBatch(batchId.trim());
            if (studentIds.isEmpty()) {
                students = Collections.emptyList();
            } else {
                List<Student> batchStudents = studentRepository.findByStudentIdIn(studentIds);
                if (!isSuperAdmin(admin)) {
                    students = batchStudents.stream()
                            .filter(s -> s.getDepartment().equalsIgnoreCase(admin.getDepartment())
                                    && s.getSection().equalsIgnoreCase(admin.getSection()))
                            .collect(Collectors.toList());
                } else {
                    students = batchStudents;
                }
            }
            subtitle = "Creation Batch: " + batchId.trim();
        } else {
            String deptFilter = (department != null && !department.isBlank()) ? department.trim() : null;
            String secFilter = (section != null && !section.isBlank()) ? section.trim() : null;
            String yearFilter = (year != null && !year.isBlank()) ? year.trim() : null;

            // Restrict regular admin to their assigned department and section
            if (!isSuperAdmin(admin)) {
                deptFilter = admin.getDepartment();
                secFilter = admin.getSection();
            }

            if (deptFilter != null && secFilter != null) {
                if (yearFilter != null) {
                    students = studentRepository.findByDepartmentIgnoreCaseAndSectionIgnoreCaseAndYearIgnoreCaseOrderByCreatedAtDesc(
                            deptFilter, secFilter, yearFilter);
                } else {
                    students = studentRepository.findByDepartmentIgnoreCaseAndSectionIgnoreCaseOrderByCreatedAtDesc(
                            deptFilter, secFilter);
                }
                subtitle = "Department: " + deptFilter + " | Section: " + secFilter + (yearFilter != null ? " | Year: " + yearFilter : "");
            } else {
                students = studentRepository.findAllByOrderByCreatedAtDesc();
                subtitle = "All Departments & Sections";
            }
        }

        return studentCredentialExportService.generateStudentCredentialsExcel(students, subtitle);
    }
}
