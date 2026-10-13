package com.classhack.config;

import com.classhack.entity.Admin;
import com.classhack.entity.AuditLog;
import com.classhack.entity.enums.Role;
import com.classhack.repository.AdminRepository;
import com.classhack.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final AdminRepository adminRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    public static final String SUPER_ADMIN_EMAIL = "admin2244@gmail.com";
    public static final String SUPER_ADMIN_RAW_PASSWORD = "admin&2244";

    @Override
    public void run(String... args) {
        ensureColumnDefaults();
        initializeSuperAdmin();
        migrateLegacyAdminRoles();
    }

    private void ensureColumnDefaults() {
        try {
            jdbcTemplate.execute("ALTER TABLE admins ALTER COLUMN activated SET DEFAULT 0");
        } catch (Exception e) {
            log.debug("Notice: Could not set default on admins.activated (it may already exist or table not ready): {}", e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE students ALTER COLUMN activated SET DEFAULT 0");
        } catch (Exception e) {
            log.debug("Notice: Could not set default on students.activated (it may already exist or table not ready): {}", e.getMessage());
        }
    }

    public void migrateLegacyAdminRoles() {
        for (Admin a : adminRepository.findAll()) {
            if ("Head of Department".equalsIgnoreCase(a.getPosition()) || "Head of Department (HOD)".equalsIgnoreCase(a.getPosition())) {
                a.setPosition("HOD");
                adminRepository.save(a);
                log.info("Migrated legacy admin role for {}: {} -> HOD", a.getEmail(), a.getPosition());
            }
        }
    }

    public void initializeSuperAdmin() {
        Optional<Admin> existing = adminRepository.findByEmailIgnoreCase(SUPER_ADMIN_EMAIL);
        if (existing.isEmpty()) {
            Admin superAdmin = Admin.builder()
                    .fullName("Super Admin")
                    .email(SUPER_ADMIN_EMAIL)
                    .password(passwordEncoder.encode(SUPER_ADMIN_RAW_PASSWORD))
                    .phone("9876543210")
                    .department("CENTRAL")
                    .section("A")
                    .position("Super Administrator")
                    .role(Role.ROLE_ADMIN)
                    .active(true)
                    .activated(true)
                    .build();

            adminRepository.save(superAdmin);

            auditLogRepository.save(AuditLog.builder()
                    .action("SUPER_ADMIN_INITIALIZED")
                    .performedBy("SYSTEM")
                    .details("Super Admin seeded: " + SUPER_ADMIN_EMAIL)
                    .build());

            log.info("Super Admin initialized successfully with email: {}", SUPER_ADMIN_EMAIL);
        } else {
            Admin admin = existing.get();
            boolean needsUpdate = false;
            if (!passwordEncoder.matches(SUPER_ADMIN_RAW_PASSWORD, admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode(SUPER_ADMIN_RAW_PASSWORD));
                needsUpdate = true;
            }
            if (!admin.isActive()) {
                admin.setActive(true);
                needsUpdate = true;
            }
            if (!admin.isActivated()) {
                admin.setActivated(true);
                needsUpdate = true;
            }
            if (admin.getRole() != Role.ROLE_ADMIN) {
                admin.setRole(Role.ROLE_ADMIN);
                needsUpdate = true;
            }
            if (needsUpdate) {
                adminRepository.save(admin);
                log.info("Super Admin credentials and status refreshed for email: {}", SUPER_ADMIN_EMAIL);
            }
        }
    }
}
