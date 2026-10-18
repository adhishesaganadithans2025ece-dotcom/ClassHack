package com.classhack.controller;

import com.classhack.dto.*;
import com.classhack.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/students")
    public ResponseEntity<StudentResponse> createStudent(@Valid @RequestBody StudentCreateRequest req) {
        return ResponseEntity.ok(adminService.createStudent(req));
    }

    @GetMapping("/students")
    public ResponseEntity<List<StudentResponse>> getAllStudents() {
        return ResponseEntity.ok(adminService.getAllStudents());
    }

    @GetMapping("/students/search")
    public ResponseEntity<List<StudentResponse>> searchStudents(@RequestParam(required = false, defaultValue = "") String keyword) {
        return ResponseEntity.ok(adminService.searchStudents(keyword));
    }

    @GetMapping("/students/{id}")
    public ResponseEntity<StudentResponse> getStudentById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getStudentById(id));
    }

    @PutMapping("/students/{id}")
    public ResponseEntity<StudentResponse> updateStudent(@PathVariable Long id, @Valid @RequestBody StudentUpdateRequest req) {
        return ResponseEntity.ok(adminService.updateStudent(id, req));
    }

    @PostMapping("/students/{id}/toggle-status")
    public ResponseEntity<Map<String, Object>> toggleStudentStatus(@PathVariable Long id) {
        adminService.toggleStudentStatus(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Student status updated successfully."));
    }

    @PostMapping("/students/{id}/reset-password")
    public ResponseEntity<Map<String, Object>> resetStudentPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String newPassword = body.get("newPassword");
        adminService.resetStudentPassword(id, newPassword);
        return ResponseEntity.ok(Map.of("success", true, "message", "Student password reset successfully."));
    }

    @GetMapping("/students/export-credentials")
    public void exportStudentCredentials(
            @RequestParam(required = false) String batchId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) String year,
            jakarta.servlet.http.HttpServletResponse response
    ) throws java.io.IOException {
        byte[] excelBytes = adminService.exportStudentCredentialsExcel(batchId, department, section, year);
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = (batchId != null && !batchId.isBlank())
                ? "ClassHack_Student_Credentials_Batch_" + timestamp + ".xlsx"
                : "ClassHack_Student_Credentials_" + timestamp + ".xlsx";

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(excelBytes.length);
        response.getOutputStream().write(excelBytes);
        response.getOutputStream().flush();
    }

    @PostMapping("/students/{id}/resend-activation")
    public ResponseEntity<Map<String, Object>> resendStudentActivation(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Email sending is disabled. Use 'Export Student Credentials (Excel)' to obtain initial credentials."
        ));
    }

    @PostMapping("/admins/resend-activation")
    public ResponseEntity<Map<String, Object>> resendAdminActivation(@RequestParam String email) {
        return ResponseEntity.ok(Map.of(
                "success", false,
                "message", "Email sending is disabled. Administrator accounts are activated upon creation."
        ));
    }

    @GetMapping("/students/{id}/history")
    public ResponseEntity<StudentParticipationHistoryResponse> getStudentParticipationHistory(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getStudentParticipationHistory(id));
    }

    @GetMapping("/dashboard-stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<List<AuditLogResponse>> getAuditLogs() {
        return ResponseEntity.ok(adminService.getAuditLogs());
    }

    @GetMapping("/admins")
    public ResponseEntity<List<AdminResponse>> getAllAdmins() {
        return ResponseEntity.ok(adminService.getAllAdmins());
    }

    @DeleteMapping("/admins/{id}")
    public ResponseEntity<Map<String, Object>> deleteAdmin(@PathVariable Long id) {
        adminService.deleteAdmin(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Administrator account deleted successfully."));
    }
}
