package com.classhack.entity;

import com.classhack.entity.enums.Gender;
import com.classhack.entity.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "students", uniqueConstraints = {
        @UniqueConstraint(columnNames = "studentId", name = "uk_student_id"),
        @UniqueConstraint(columnNames = "email", name = "uk_student_email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String studentId;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Gender gender;

    @Column(nullable = false, length = 50)
    private String department;

    @Column(nullable = false, length = 20)
    private String section;

    @Column(name = "year_level", nullable = false, length = 20)
    private String year;

    @Column(nullable = false, length = 30)
    private String academicYear;

    @Column(length = 20)
    private String phone;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Role role = Role.ROLE_STUDENT;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
