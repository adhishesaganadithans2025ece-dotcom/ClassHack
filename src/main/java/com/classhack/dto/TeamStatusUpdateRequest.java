package com.classhack.dto;

import com.classhack.entity.enums.TeamStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamStatusUpdateRequest {

    @NotNull(message = "Team status is required")
    private TeamStatus status;
}
