package com.classhack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentSearchResponse {
    private Long id;
    private String studentId;
    private String fullName;
    private String department;
    private String section;
    private String year;
    private String email;
    private boolean alreadyInTeam;
}
