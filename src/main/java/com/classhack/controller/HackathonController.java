package com.classhack.controller;

import com.classhack.dto.HackathonCreateRequest;
import com.classhack.dto.HackathonResponse;
import com.classhack.entity.enums.Role;
import com.classhack.security.CustomUserDetails;
import com.classhack.service.AuthService;
import com.classhack.service.HackathonService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/hackathons")
@RequiredArgsConstructor
public class HackathonController {

    private final HackathonService hackathonService;
    private final AuthService authService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HackathonResponse> createHackathon(@Valid @RequestBody HackathonCreateRequest req) {
        return ResponseEntity.ok(hackathonService.createHackathon(req));
    }

    @GetMapping
    public ResponseEntity<List<HackathonResponse>> getAllHackathons() {
        CustomUserDetails user = authService.getCurrentUser();
        boolean isStudent = (user != null && user.getRole() == Role.ROLE_STUDENT);
        Long studentId = isStudent ? user.getId() : null;
        return ResponseEntity.ok(hackathonService.getAllHackathons(studentId, isStudent));
    }

    @GetMapping("/search")
    public ResponseEntity<List<HackathonResponse>> searchHackathons(@RequestParam(required = false, defaultValue = "") String keyword) {
        CustomUserDetails user = authService.getCurrentUser();
        boolean isStudent = (user != null && user.getRole() == Role.ROLE_STUDENT);
        Long studentId = isStudent ? user.getId() : null;
        return ResponseEntity.ok(hackathonService.searchHackathons(keyword, studentId, isStudent));
    }

    @GetMapping("/{id}")
    public ResponseEntity<HackathonResponse> getHackathonById(@PathVariable Long id) {
        CustomUserDetails user = authService.getCurrentUser();
        Long studentId = (user != null && user.getRole() == Role.ROLE_STUDENT) ? user.getId() : null;
        return ResponseEntity.ok(hackathonService.getHackathonById(id, studentId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<HackathonResponse> updateHackathon(@PathVariable Long id, @Valid @RequestBody HackathonCreateRequest req) {
        return ResponseEntity.ok(hackathonService.updateHackathon(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteHackathon(@PathVariable Long id) {
        hackathonService.deleteHackathon(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Hackathon deleted successfully."));
    }
}
