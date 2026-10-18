package com.classhack.controller;

import com.classhack.dto.NotificationResponse;
import com.classhack.dto.StudentParticipationHistoryResponse;
import com.classhack.dto.StudentResponse;
import com.classhack.dto.StudentSearchResponse;
import com.classhack.dto.TeamResponse;
import com.classhack.security.CustomUserDetails;
import com.classhack.service.AdminService;
import com.classhack.service.AuthService;
import com.classhack.service.StudentService;
import com.classhack.service.TeamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/student")
@PreAuthorize("hasRole('STUDENT')")
public class StudentController {

    private final StudentService studentService;
    private final AdminService adminService;
    private final TeamService teamService;
    private final AuthService authService;

    public StudentController(
            StudentService studentService,
            AdminService adminService,
            TeamService teamService,
            AuthService authService) {
        this.studentService = studentService;
        this.adminService = adminService;
        this.teamService = teamService;
        this.authService = authService;
    }

    @GetMapping("/profile")
    public ResponseEntity<StudentResponse> getProfile() {
        CustomUserDetails user = authService.getCurrentUser();
        return ResponseEntity.ok(studentService.getStudentProfile(user.getId()));
    }

    @GetMapping("/teams")
    public ResponseEntity<List<TeamResponse>> getMyTeams() {
        CustomUserDetails user = authService.getCurrentUser();
        return ResponseEntity.ok(teamService.getStudentTeams(user.getId()));
    }

    @GetMapping("/history")
    public ResponseEntity<StudentParticipationHistoryResponse> getMyParticipationHistory() {
        CustomUserDetails user = authService.getCurrentUser();
        return ResponseEntity.ok(adminService.getStudentParticipationHistory(user.getId()));
    }

    @GetMapping("/search-members")
    public ResponseEntity<List<StudentSearchResponse>> searchMembers(
            @RequestParam(required = false, defaultValue = "") String query,
            @RequestParam(required = false) Long hackathonId) {
        return ResponseEntity.ok(studentService.searchActiveStudentsForTeam(query, hackathonId));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<NotificationResponse>> getNotifications() {
        CustomUserDetails user = authService.getCurrentUser();
        return ResponseEntity.ok(studentService.getStudentNotifications(user.getId()));
    }

    @GetMapping("/notifications/unread-count")
    public ResponseEntity<Map<String, Object>> getUnreadCount() {
        CustomUserDetails user = authService.getCurrentUser();
        long count = studentService.getUnreadNotificationCount(user.getId());
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    @PostMapping("/notifications/{id}/read")
    public ResponseEntity<Map<String, Object>> markAsRead(@PathVariable Long id) {
        CustomUserDetails user = authService.getCurrentUser();
        studentService.markNotificationAsRead(id, user.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/notifications/read-all")
    public ResponseEntity<Map<String, Object>> markAllAsRead() {
        CustomUserDetails user = authService.getCurrentUser();
        studentService.markAllNotificationsAsRead(user.getId());
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@jakarta.validation.Valid @RequestBody com.classhack.dto.ChangePasswordRequest req) {
        authService.changePassword(req);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully."));
    }
}
