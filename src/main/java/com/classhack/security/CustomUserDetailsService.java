package com.classhack.security;

import com.classhack.entity.Admin;
import com.classhack.entity.Student;
import com.classhack.repository.AdminRepository;
import com.classhack.repository.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String trimmed = username != null ? username.trim() : "";

        // First check if an admin exists with this email
        Optional<Admin> admin = adminRepository.findByEmailIgnoreCase(trimmed);
        if (admin.isPresent()) {
            return new CustomUserDetails(admin.get());
        }

        // Next check if a student exists by email
        Optional<Student> studentByEmail = studentRepository.findByEmailIgnoreCase(trimmed);
        if (studentByEmail.isPresent()) {
            return new CustomUserDetails(studentByEmail.get());
        }

        // Next check if a student exists by studentId (e.g. "ECE001")
        Optional<Student> studentById = studentRepository.findByStudentIdIgnoreCase(trimmed);
        if (studentById.isPresent()) {
            return new CustomUserDetails(studentById.get());
        }

        // Next check if a student exists by register / roll number (e.g. "710021106001")
        Optional<Student> studentByRoll = studentRepository.findByRollNumberIgnoreCase(trimmed);
        if (studentByRoll.isPresent()) {
            return new CustomUserDetails(studentByRoll.get());
        }

        throw new UsernameNotFoundException("No user found with username / email / Student ID / Roll Number: " + username);
    }
}
