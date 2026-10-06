package com.classhack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TeamMemberResponse {
    private Long id;
    private Long studentDbId;
    private String studentId;
    private String fullName;
    private String department;
    private String section;
    private String year;
    private String role; // "Leader" or "Member"
    private String email;
    private String phone;
}
