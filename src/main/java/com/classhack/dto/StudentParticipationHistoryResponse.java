package com.classhack.dto;

import com.classhack.entity.enums.TeamStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentParticipationHistoryResponse {
    private Long studentDbId;
    private String studentId;
    private String fullName;
    private String department;
    private String section;
    private String year;
    private String academicYear;
    private String email;
    private String phone;
    private long totalHackathons;
    private long totalWins;
    private long totalFinals;
    private long totalShortlisted;

    @Builder.Default
    private List<ParticipationItem> participations = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParticipationItem {
        private Long hackathonId;
        private String hackathonName;
        private String organizedBy;
        private String mode;
        private LocalDate date;
        private Long teamId;
        private String teamName;
        private String role; // "Leader" or "Member"
        private TeamStatus status;
        private String achievementBadge;
    }
}
