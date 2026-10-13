package com.classhack.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class EmailService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    private final ObjectMapper objectMapper;
    private HttpClient httpClient;

    @Value("${app.mail.resend-api-url:https://api.resend.com/emails}")
    private String resendApiUrl;

    @Value("${app.mail.resend-api-key:}")
    private String resendApiKey;

    @Value("${app.mail.from:${mail.from:ClassHack <onboarding@resend.dev>}}")
    private String mailFrom;

    @Value("${app.base-url:http://localhost:8080}")
    private String appBaseUrl;

    public record EmailResult(boolean success, String message) {}

    public EmailService() {
        this(new ObjectMapper());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public EmailService(@org.springframework.beans.factory.annotation.Autowired(required = false) ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void setResendApiKey(String resendApiKey) {
        this.resendApiKey = resendApiKey;
    }

    public void setResendApiUrl(String resendApiUrl) {
        this.resendApiUrl = resendApiUrl;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public String getAppBaseUrl() {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            return "http://localhost:8080";
        }
        return appBaseUrl.endsWith("/") ? appBaseUrl.substring(0, appBaseUrl.length() - 1) : appBaseUrl;
    }

    public EmailResult sendStudentWelcomeEmail(String studentName, String rollNumber, String recipientEmail, String rawToken) {
        if (!isValidEmail(recipientEmail)) {
            return new EmailResult(false, "Invalid recipient email address.");
        }

        String baseUrl = getAppBaseUrl();
        String loginUrl = baseUrl + "/login.html";
        String activationUrl = baseUrl + "/activate.html?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        String subject = "Welcome to ClassHack — Your Student Account Details";

        String plainText = String.format(
                "Dear %s,%n%n" +
                "Your student account has been successfully created on ClassHack by your class administrator.%n%n" +
                "Your account details are provided below:%n%n" +
                "- Name: %s%n" +
                "- Roll Number: %s%n" +
                "- Registered Email: %s%n" +
                "- Login Page: %s%n%n" +
                "Please use your registered email address and the password setup instructions to access your account.%n%n" +
                "For your security, please set your password using the secure account activation link provided below:%n" +
                "%s%n%n" +
                "This activation link is personal to you and should not be shared with anyone.%n%n" +
                "If you did not expect this account, please contact your class administrator.%n%n" +
                "Regards,%nClassHack Team",
                studentName, studentName, rollNumber, recipientEmail, loginUrl, activationUrl
        );

        String htmlContent = buildEmailTemplate(
                "Student Account Created",
                studentName,
                "Your student account has been successfully created on ClassHack by your class administrator.",
                new String[][]{
                        {"Name", studentName},
                        {"Roll Number", rollNumber},
                        {"Registered Email", recipientEmail},
                        {"Login Page", String.format("<a href=\"%s\" style=\"color:#2563EB;\">%s</a>", loginUrl, loginUrl)}
                },
                "Please use your registered email address and the password setup instructions to access your account. For your security, please set your password using the secure account activation link provided below.",
                activationUrl,
                "Activate Your Account / Set Password",
                "This activation link is personal to you and should not be shared with anyone. If you did not expect this account, please contact your class administrator."
        );

        return sendViaResendApi(recipientEmail, subject, plainText, htmlContent);
    }

    public EmailResult sendAdminWelcomeEmail(String adminName, String designation, String recipientEmail, String rawToken) {
        if (!isValidEmail(recipientEmail)) {
            return new EmailResult(false, "Invalid recipient email address.");
        }

        String baseUrl = getAppBaseUrl();
        String loginUrl = baseUrl + "/login.html";
        String activationUrl = baseUrl + "/activate.html?token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);

        String subject = "Welcome to ClassHack — Your Admin Account Details";

        String plainText = String.format(
                "Dear %s,%n%n" +
                "Your administrator account has been successfully created on ClassHack.%n%n" +
                "Your account details are provided below:%n%n" +
                "- Name: %s%n" +
                "- Registered Email: %s%n" +
                "- Designation: %s%n" +
                "- Login Page: %s%n%n" +
                "To access your account, please use the secure password setup link below to create your password:%n" +
                "%s%n%n" +
                "Once your account is activated, you can log in using your registered email address and your chosen password.%n%n" +
                "If you need assistance, please contact the administrator who created your account.%n%n" +
                "Regards,%nClassHack Team",
                adminName, adminName, recipientEmail, designation, loginUrl, activationUrl
        );

        String htmlContent = buildEmailTemplate(
                "Administrator Account Created",
                adminName,
                "Your administrator account has been successfully created on ClassHack.",
                new String[][]{
                        {"Name", adminName},
                        {"Registered Email", recipientEmail},
                        {"Designation", designation},
                        {"Login Page", String.format("<a href=\"%s\" style=\"color:#2563EB;\">%s</a>", loginUrl, loginUrl)}
                },
                "To access your account, please use the secure password setup link below to create your password. Once your account is activated, you can log in using your registered email address and your chosen password.",
                activationUrl,
                "Activate Your Account / Set Password",
                "If you need assistance, please contact the administrator who created your account."
        );

        return sendViaResendApi(recipientEmail, subject, plainText, htmlContent);
    }

    public EmailResult sendViaResendApi(String recipient, String subject, String plainText, String htmlContent) {
        if (resendApiKey == null || resendApiKey.trim().isBlank() || "test_api_key".equalsIgnoreCase(resendApiKey.trim())) {
            log.info("Resend API key not configured (RESEND_API_KEY unset). Skipped HTTPS email transmission to: {}", maskEmail(recipient));
            return new EmailResult(false, "Resend API key not configured. Please set RESEND_API_KEY in environment variables.");
        }

        try {
            Map<String, Object> payload = Map.of(
                    "from", mailFrom,
                    "to", List.of(recipient.trim()),
                    "subject", subject,
                    "html", htmlContent,
                    "text", plainText
            );

            String requestBodyJson = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(resendApiUrl))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "ClassHack-App/1.0")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                String emailId = extractResendId(responseBody);
                log.info("Email accepted by Resend API (id: {}) for recipient: {}", emailId, maskEmail(recipient));
                return new EmailResult(true, "Email accepted for delivery by Resend API.");
            } else if (statusCode == 401 || statusCode == 403) {
                log.warn("Resend API authentication failed (HTTP {}): Invalid or missing API key.", statusCode);
                return new EmailResult(false, "Resend API authentication failed: Invalid or expired API key.");
            } else if (statusCode == 422) {
                String errorMsg = extractResendErrorMessage(responseBody);
                log.warn("Resend sender validation error (HTTP 422): {}", errorMsg);
                return new EmailResult(false, "Resend validation error: " + errorMsg);
            } else if (statusCode == 429) {
                log.warn("Resend API rate limit exceeded (HTTP 429) for recipient: {}", maskEmail(recipient));
                return new EmailResult(false, "Resend rate limit exceeded (HTTP 429). Please retry in a few moments.");
            } else {
                log.warn("Resend API server error (HTTP {}) for recipient: {}", statusCode, maskEmail(recipient));
                return new EmailResult(false, "Resend service error (HTTP " + statusCode + "). Please retry later.");
            }
        } catch (java.net.http.HttpTimeoutException ex) {
            log.warn("Resend API request timed out for recipient {}: {}", maskEmail(recipient), ex.getMessage());
            return new EmailResult(false, "Resend API request timed out.");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Resend email sending interrupted for recipient {}: {}", maskEmail(recipient), ex.getMessage());
            return new EmailResult(false, "Email dispatch interrupted.");
        } catch (Exception ex) {
            log.warn("Unexpected error sending email via Resend API to {}: {}", maskEmail(recipient), ex.getMessage());
            return new EmailResult(false, "Failed to send email via Resend API: " + ex.getMessage());
        }
    }

    private String extractResendId(String responseBody) {
        try {
            if (responseBody != null && !responseBody.isBlank()) {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("id")) {
                    return root.get("id").asText();
                }
            }
        } catch (Exception ignored) {}
        return "unknown";
    }

    private String extractResendErrorMessage(String responseBody) {
        try {
            if (responseBody != null && !responseBody.isBlank()) {
                JsonNode root = objectMapper.readTree(responseBody);
                if (root.has("message")) {
                    return root.get("message").asText();
                }
            }
        } catch (Exception ignored) {}
        return "Sender or domain is unverified in Resend.";
    }

    private boolean isValidEmail(String email) {
        return email != null && !email.isBlank() && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return email;
        return email.charAt(0) + "***" + email.substring(atIndex);
    }

    private String buildEmailTemplate(String badgeTitle, String recipientName, String introText,
                                       String[][] details, String instructions, String actionUrl,
                                       String actionButtonText, String noteText) {
        StringBuilder tableRows = new StringBuilder();
        for (String[] detail : details) {
            tableRows.append(String.format(
                    "<tr>" +
                    "<td style=\"padding:10px 14px; color:#6B7280; font-size:14px; font-weight:600; width:35%%; border-bottom:1px solid #E5E7EB;\">%s</td>" +
                    "<td style=\"padding:10px 14px; color:#111827; font-size:14px; font-weight:500; border-bottom:1px solid #E5E7EB;\">%s</td>" +
                    "</tr>",
                    detail[0], detail[1]
            ));
        }

        return "<!DOCTYPE html>" +
                "<html><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"></head>" +
                "<body style=\"margin:0; padding:0; background-color:#F3F4F6; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;\">" +
                "<div style=\"max-width:600px; margin:30px auto; background:#FFFFFF; border-radius:12px; overflow:hidden; box-shadow:0 4px 14px rgba(0,0,0,0.08); border:1px solid #E5E7EB;\">" +
                "  <!-- Header -->" +
                "  <div style=\"background:linear-gradient(135deg, #1E3A8A 0%, #2563EB 100%); padding:28px 32px; text-align:center;\">" +
                "    <div style=\"display:inline-block; background:rgba(255,255,255,0.18); color:#FFFFFF; padding:4px 14px; border-radius:20px; font-size:12px; font-weight:700; letter-spacing:0.8px; text-transform:uppercase; margin-bottom:8px;\">" +
                badgeTitle +
                "    </div>" +
                "    <h1 style=\"margin:0; color:#FFFFFF; font-size:26px; font-weight:800; letter-spacing:1px;\">CLASSHACK</h1>" +
                "    <p style=\"margin:6px 0 0; color:#E0E7FF; font-size:14px;\">Class Hackathon Registration &amp; Management Platform</p>" +
                "  </div>" +
                "  <!-- Body -->" +
                "  <div style=\"padding:32px;\">" +
                "    <p style=\"margin:0 0 16px; font-size:16px; color:#111827;\">Dear <strong>" + recipientName + "</strong>,</p>" +
                "    <p style=\"margin:0 0 20px; font-size:15px; color:#374151; line-height:1.6;\">" + introText + "</p>" +
                "    <!-- Details Table -->" +
                "    <table style=\"width:100%; border-collapse:collapse; background:#F9FAFB; border-radius:8px; overflow:hidden; border:1px solid #E5E7EB; margin-bottom:24px;\">" +
                tableRows +
                "    </table>" +
                "    <p style=\"margin:0 0 24px; font-size:14px; color:#4B5563; line-height:1.6;\">" + instructions + "</p>" +
                "    <!-- Action Button -->" +
                "    <div style=\"text-align:center; margin:28px 0;\">" +
                "      <a href=\"" + actionUrl + "\" style=\"display:inline-block; background-color:#2563EB; color:#FFFFFF; font-size:15px; font-weight:700; text-decoration:none; padding:14px 32px; border-radius:8px; box-shadow:0 4px 10px rgba(37,99,235,0.3);\">" +
                actionButtonText +
                "      </a>" +
                "    </div>" +
                "    <div style=\"background:#EFF6FF; border-left:4px solid #2563EB; padding:14px; border-radius:4px; margin-bottom:24px;\">" +
                "      <p style=\"margin:0; font-size:13px; color:#1E40AF; line-height:1.5;\">" + noteText + "</p>" +
                "    </div>" +
                "    <p style=\"margin:0; font-size:14px; color:#6B7280;\">Regards,<br><strong style=\"color:#111827;\">ClassHack Team</strong></p>" +
                "  </div>" +
                "  <!-- Footer -->" +
                "  <div style=\"background:#F9FAFB; padding:18px 32px; text-align:center; border-top:1px solid #E5E7EB; color:#9CA3AF; font-size:12px;\">" +
                "    This is an automated notification from ClassHack. Please do not reply directly to this email." +
                "  </div>" +
                "</div>" +
                "</body></html>";
    }
}
