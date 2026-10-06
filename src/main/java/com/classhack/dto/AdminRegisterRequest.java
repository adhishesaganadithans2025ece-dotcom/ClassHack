package com.classhack.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminRegisterRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @NotBlank(message = "Phone number is required")
    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email address is required")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Section is required")
    private String section;

    @NotBlank(message = "Position is required")
    private String position;

    @NotBlank(message = "Authorizing administrator email is required")
    @Email(message = "Valid authorizing administrator email is required")
    private String authorizerEmail;

    @NotBlank(message = "Authorizing administrator password is required")
    private String authorizerPassword;
}
