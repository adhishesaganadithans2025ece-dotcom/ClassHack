package com.classhack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsResponse {
    private long totalStudents;
    private long totalHackathons;
    private long openHackathons;
    private long closedHackathons;
    private long totalTeams;
    private long totalParticipants;
    private long shortlistedTeams;
    private long finalists;
    private long winners;
}
