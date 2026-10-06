package com.classhack.controller;

import com.classhack.dto.TeamCreateRequest;
import com.classhack.dto.TeamMemberAddRequest;
import com.classhack.dto.TeamResponse;
import com.classhack.dto.TeamStatusUpdateRequest;
import com.classhack.entity.enums.Role;
import com.classhack.security.CustomUserDetails;
import com.classhack.service.AuthService;
import com.classhack.service.TeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final AuthService authService;

    @PostMapping("/api/hackathons/{hackathonId}/teams")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<TeamResponse> createTeam(@PathVariable Long hackathonId,
                                                   @Valid @RequestBody TeamCreateRequest req) {
        CustomUserDetails user = authService.getCurrentUser();
        return ResponseEntity.ok(teamService.createTeam(hackathonId, req, user.getId()));
    }

    @GetMapping("/api/hackathons/{hackathonId}/teams")
    public ResponseEntity<List<TeamResponse>> getTeamsByHackathon(@PathVariable Long hackathonId) {
        return ResponseEntity.ok(teamService.getTeamsByHackathon(hackathonId));
    }

    @GetMapping("/api/teams/{id}")
    public ResponseEntity<TeamResponse> getTeamById(@PathVariable Long id) {
        return ResponseEntity.ok(teamService.getTeamById(id));
    }

    @PutMapping("/api/teams/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<TeamResponse> updateTeamStatus(@PathVariable Long id,
                                                         @Valid @RequestBody TeamStatusUpdateRequest req) {
        return ResponseEntity.ok(teamService.updateTeamStatus(id, req.getStatus()));
    }

    @PostMapping("/api/teams/{id}/members")
    public ResponseEntity<TeamResponse> addTeamMember(@PathVariable Long id,
                                                      @Valid @RequestBody TeamMemberAddRequest req) {
        return ResponseEntity.ok(teamService.addTeamMember(id, req.getStudentId()));
    }

    @DeleteMapping("/api/teams/{id}/members/{studentId}")
    public ResponseEntity<TeamResponse> removeTeamMember(@PathVariable Long id,
                                                         @PathVariable Long studentId) {
        return ResponseEntity.ok(teamService.removeTeamMember(id, studentId));
    }

    @DeleteMapping("/api/teams/{id}")
    public ResponseEntity<Map<String, Object>> deleteTeam(@PathVariable Long id) {
        teamService.deleteTeam(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Team deleted successfully."));
    }
}
