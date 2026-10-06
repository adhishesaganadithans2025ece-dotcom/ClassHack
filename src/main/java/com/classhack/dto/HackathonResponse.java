package com.classhack.dto;

import com.classhack.entity.enums.HackathonMode;
import com.classhack.entity.enums.HackathonStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HackathonResponse {
    private Long id;
    private String name;
    private String description;
    private String organizedBy;
    private LocalDate registrationStart;
    private LocalDate registrationEnd;
    private Integer maximumTeamSize;
    private Double prizeMoney;
    private HackathonMode mode;
    private String venue;
    private String registrationLink;
    private HackathonStatus status;
    private String createdByAdminName;
    private long totalTeams;
    private long totalParticipants;
    private boolean isRegistrationOpen;
    private boolean alreadyRegistered;
    private Long studentTeamId;
    private LocalDateTime createdAt;
}
