package com.classhack.security;

import com.classhack.entity.Admin;
import com.classhack.entity.Student;
import com.classhack.entity.enums.Role;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long id;
    private final String username; // email
    private final String password;
    private final Role role;
    private final String fullName;
    private final String email;
    private final String studentId;
    private final String department;
    private final String section;
    private final String position;
    private final boolean active;
    private final boolean activated;
    private final boolean passwordChangeRequired;
    private final String rollNumber;
    private final Collection<? extends GrantedAuthority> authorities;

    public CustomUserDetails(Admin admin) {
        this.id = admin.getId();
        this.username = admin.getEmail();
        this.password = admin.getPassword();
        this.role = admin.getRole();
        this.fullName = admin.getFullName();
        this.email = admin.getEmail();
        this.studentId = null;
        this.department = admin.getDepartment();
        this.section = admin.getSection();
        this.position = admin.getPosition();
        this.active = admin.isActive();
        this.activated = admin.isActivated();
        this.passwordChangeRequired = false;
        this.rollNumber = null;
        this.authorities = List.of(new SimpleGrantedAuthority(admin.getRole().name()));
    }

    public CustomUserDetails(Student student) {
        this.id = student.getId();
        this.username = student.getEmail();
        this.password = student.getPassword();
        this.role = student.getRole();
        this.fullName = student.getFullName();
        this.email = student.getEmail();
        this.studentId = student.getStudentId();
        this.department = student.getDepartment();
        this.section = student.getSection();
        this.position = null;
        this.active = student.isActive();
        this.activated = student.isActivated();
        this.passwordChangeRequired = student.isPasswordChangeRequired();
        this.rollNumber = student.getRollNumber();
        this.authorities = List.of(new SimpleGrantedAuthority(student.getRole().name()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }
}
