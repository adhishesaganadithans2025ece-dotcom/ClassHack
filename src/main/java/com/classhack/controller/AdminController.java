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
