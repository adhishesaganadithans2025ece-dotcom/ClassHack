package com.classhack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private boolean success;
    private String message;
    private String role; // "ROLE_ADMIN" or "ROLE_STUDENT"
    private String fullName;
    private String email;
    private String studentId;
    private String department;
    private String section;
    private String redirectUrl;
    private boolean passwordChangeRequired;
    private boolean emailSent;
    private String emailMessage;
}
