package com.classhack.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TokenVerificationResponse {
    private boolean valid;
    private String email;
    private String fullName;
    private String role;
    private String designationOrId;
    private String message;
}
