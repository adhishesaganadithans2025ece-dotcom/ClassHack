package com.classhack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminResponse {
    private Long id;
    private String fullName;
    private String email;
    private String phone;
    private String department;
    private String section;
    private String position;
    private String role;
    private boolean active;
    private boolean isSuperAdmin;
    private LocalDateTime createdAt;
}
