package com.classhack.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMemberAddRequest {

    @NotNull(message = "Student ID is required")
    private Long studentId;
}
