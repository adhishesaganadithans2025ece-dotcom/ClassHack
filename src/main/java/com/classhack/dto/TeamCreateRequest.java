package com.classhack.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamCreateRequest {

    @NotBlank(message = "Team name is required")
    @Size(max = 100, message = "Team name cannot exceed 100 characters")
    private String teamName;

    // Additional member IDs (excluding the leader who is added automatically)
    @Builder.Default
    private List<Long> memberStudentIds = new ArrayList<>();
}
