package com.classhack.dto;

import com.classhack.entity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudentResponse {
    private Long id;
    private String studentId;
    private String fullName;
    private Gender gender;
    private String department;
    private String section;
    private String year;
    private String academicYear;
    private String phone;
    private String email;
    private String rollNumber;
    private boolean active;
    private boolean activated;
    private boolean passwordChangeRequired;
    private String batchId;
    private boolean emailSent;
    private String emailMessage;
    private LocalDateTime createdAt;
    private long totalHackathons;
    private long totalWins;
    private long totalFinals;
    private long totalShortlisted;
}
