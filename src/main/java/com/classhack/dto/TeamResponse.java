package com.classhack.dto;

import com.classhack.entity.enums.TeamStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamResponse {
    private Long id;
    private String teamName;
    private Long hackathonId;
    private String hackathonName;
    private String hackathonMode;
    private String hackathonVenue;
    private Integer maximumTeamSize;
    private Long teamLeaderId;
    private String teamLeaderName;
    private String teamLeaderStudentId;
    private TeamStatus status;
    private int memberCount;
    @Builder.Default
    private List<TeamMemberResponse> members = new ArrayList<>();
    private LocalDateTime createdAt;
}
