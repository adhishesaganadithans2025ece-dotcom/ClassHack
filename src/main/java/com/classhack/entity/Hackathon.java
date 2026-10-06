package com.classhack.entity;

import com.classhack.entity.enums.HackathonMode;
import com.classhack.entity.enums.HackathonStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "hackathons")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hackathon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 150)
    private String organizedBy;

    @Column(nullable = false)
    private LocalDate registrationStart;

    @Column(nullable = false)
    private LocalDate registrationEnd;

    @Column(nullable = false)
    private Integer maximumTeamSize;

    @Column(nullable = false)
    @Builder.Default
    private Double prizeMoney = 0.0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private HackathonMode mode;

    @Column(length = 200)
    private String venue;

    @Column(nullable = false, length = 500)
    private String registrationLink;

    @Column(length = 100)
    private String createdByAdminName;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private HackathonStatus statusOverride;

    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    public HackathonStatus getComputedStatus() {
        if (statusOverride == HackathonStatus.COMPLETED) {
            return HackathonStatus.COMPLETED;
        }
        LocalDate today = LocalDate.now();
        if (registrationStart != null && today.isBefore(registrationStart)) {
            return HackathonStatus.UPCOMING;
        }
        if (registrationEnd != null && today.isAfter(registrationEnd)) {
            return HackathonStatus.REGISTRATION_CLOSED;
        }
        return HackathonStatus.REGISTRATION_OPEN;
    }

    public boolean isRegistrationOpen() {
        return getComputedStatus() == HackathonStatus.REGISTRATION_OPEN;
    }
}
