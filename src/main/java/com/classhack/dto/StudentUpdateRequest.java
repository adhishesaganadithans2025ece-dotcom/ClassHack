package com.classhack.dto;

import com.classhack.entity.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentUpdateRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name cannot exceed 100 characters")
    private String fullName;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotBlank(message = "Department is required")
    private String department;

    @NotBlank(message = "Section is required")
    private String section;

    @NotBlank(message = "Year is required")
    private String year;

    @NotBlank(message = "Academic year is required")
    private String academicYear;

    private String phone;

    @NotBlank(message = "Email is required")
    @Email(message = "Valid email is required")
    private String email;

    private Boolean active;

    private String newPassword; // Optional: If admin wants to reset password
}
