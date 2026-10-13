package com.classhack.service;

import com.classhack.dto.*;
import com.classhack.entity.Notification;
import com.classhack.entity.Student;
import com.classhack.exception.ResourceNotFoundException;
import com.classhack.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final NotificationRepository notificationRepository;
    private final AdminService adminService;
    private final HackathonService hackathonService;
    private final TeamService teamService;

    public StudentResponse getStudentProfile(Long studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        return adminService.getStudentById(student.getId());
    }

    public List<StudentSearchResponse> searchActiveStudentsForTeam(String query) {
        return searchActiveStudentsForTeam(query, null);
    }

    public List<StudentSearchResponse> searchActiveStudentsForTeam(String query, Long hackathonId) {
        String trimmed = query != null ? query.trim() : "";
        List<Student> students = trimmed.isEmpty()
                ? studentRepository.findByActiveTrueOrderByFullNameAsc()
                : studentRepository.searchActiveStudentsForTeam(trimmed);

        return students.stream()
                .map(s -> {
                    boolean alreadyInTeam = false;
                    if (hackathonId != null) {
                        alreadyInTeam = teamMemberRepository.isStudentInHackathonTeam(s.getId(), hackathonId);
                    }
                    return StudentSearchResponse.builder()
                            .id(s.getId())
                            .studentId(s.getStudentId())
                            .fullName(s.getFullName())
                            .department(s.getDepartment())
                            .section(s.getSection())
                            .year(s.getYear())
                            .email(s.getEmail())
                            .alreadyInTeam(alreadyInTeam)
                            .build();
                })
                .collect(Collectors.toList());
    }

    public List<NotificationResponse> getStudentNotifications(Long studentId) {
        return notificationRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(n -> NotificationResponse.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .message(n.getMessage())
                        .readStatus(n.isReadStatus())
                        .createdAt(n.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public long getUnreadNotificationCount(Long studentId) {
        return notificationRepository.countByStudentIdAndReadStatusFalse(studentId);
    }

    @Transactional
    public void markNotificationAsRead(Long notificationId, Long studentId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found with ID: " + notificationId));

        if (notification.getStudent().getId().equals(studentId)) {
            notification.setReadStatus(true);
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllNotificationsAsRead(Long studentId) {
        List<Notification> unread = notificationRepository.findByStudentIdOrderByCreatedAtDesc(studentId);
        for (Notification n : unread) {
            if (!n.isReadStatus()) {
                n.setReadStatus(true);
                notificationRepository.save(n);
            }
        }
    }
}
