package com.classhack.controller;

import com.classhack.dto.*;
import com.classhack.security.CustomUserDetails;
import com.classhack.service.AccountActivationService;
import com.classhack.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccountActivationService accountActivationService;

    @GetMapping("/verify-activation-token")
    public ResponseEntity<TokenVerificationResponse> verifyActivationToken(@RequestParam String token) {
        return ResponseEntity.ok(accountActivationService.verifyActivationToken(token));
    }

    @PostMapping("/activate-account")
    public ResponseEntity<Map<String, Object>> activateAccount(@Valid @RequestBody AccountActivationRequest req) {
        accountActivationService.activateAccount(req);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Account successfully activated! You can now log in.",
                "redirectUrl", "/login.html"
        ));
    }

    @PostMapping("/register-admin")
    public ResponseEntity<LoginResponse> registerAdmin(@Valid @RequestBody AdminRegisterRequest req) {
        return ResponseEntity.ok(authService.registerAdmin(req));
    }

    @PostMapping("/verify-admin")
    public ResponseEntity<Map<String, Object>> verifyAdmin(@RequestBody Map<String, String> creds) {
        String email = creds != null ? creds.get("email") : null;
        String password = creds != null ? creds.get("password") : null;
        return ResponseEntity.ok(authService.verifyAdmin(email, password));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        return ResponseEntity.ok(authService.login(req, request, response));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser() {
        CustomUserDetails user = authService.getCurrentUser();
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("authenticated", false));
        }

        boolean isSuperAdmin = com.classhack.config.DataInitializer.SUPER_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail());

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("authenticated", true);
        resp.put("id", user.getId());
        resp.put("fullName", user.getFullName());
        resp.put("email", user.getEmail());
        resp.put("role", user.getRole().name());
        resp.put("isSuperAdmin", isSuperAdmin);
        resp.put("studentId", user.getStudentId() != null ? user.getStudentId() : "");
        resp.put("department", user.getDepartment() != null ? user.getDepartment() : "");
        resp.put("section", user.getSection() != null ? user.getSection() : "");
        resp.put("position", user.getPosition() != null ? user.getPosition() : "");
        resp.put("passwordChangeRequired", user.isPasswordChangeRequired());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(req);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully."));
    }
}
