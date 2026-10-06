package com.classhack.dto;

import com.classhack.entity.enums.HackathonMode;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.URL;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HackathonCreateRequest {

    @NotBlank(message = "Hackathon name is required")
    @Size(max = 150, message = "Name cannot exceed 150 characters")
    private String name;

    private String description;

    @NotBlank(message = "Organized By is required")
    private String organizedBy;

    @NotNull(message = "Registration start date is required")
    private LocalDate registrationStart;

    @NotNull(message = "Registration end date is required")
    private LocalDate registrationEnd;

    @NotNull(message = "Maximum team size is required")
    @Min(value = 1, message = "Maximum team size must be at least 1")
    private Integer maximumTeamSize;

    @Min(value = 0, message = "Prize money cannot be negative")
    private Double prizeMoney;

    @NotNull(message = "Hackathon mode is required (ONLINE or OFFLINE)")
    private HackathonMode mode;

    private String venue;

    @NotBlank(message = "Official registration link is required")
    @URL(message = "Must be a valid URL (e.g., https://...)")
    private String registrationLink;

    private Boolean confirmDuplicate;
}
