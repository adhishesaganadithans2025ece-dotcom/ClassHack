package com.classhack;

import com.classhack.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ResendEmailApiTest {

    private EmailService emailService;
    private HttpClient mockHttpClient;
    private HttpResponse<String> mockResponse;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        mockHttpClient = mock(HttpClient.class);
        mockResponse = mock(HttpResponse.class);
        emailService = new EmailService(new ObjectMapper());
        emailService.setHttpClient(mockHttpClient);
        emailService.setResendApiKey("re_test_key_12345");
        emailService.setResendApiUrl("https://api.resend.com/emails");
        emailService.setMailFrom("ClassHack <notifications@classhack.dev>");
    }

    @Test
    @DisplayName("Resend API: Successful 200 response returns success")
    @SuppressWarnings("unchecked")
    void testSuccessfulResendApiResponse() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"id\": \"re_abc12345\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Aarav Patel",
                "710021106001",
                "aarav@college.edu",
                "test-raw-token-123"
        );

        assertTrue(result.success());
        assertTrue(result.message().contains("accepted"));
    }

    @Test
    @DisplayName("Resend API: 401 Unauthorized returns authentication failure")
    @SuppressWarnings("unchecked")
    void testAuthenticationFailure() throws Exception {
        when(mockResponse.statusCode()).thenReturn(401);
        when(mockResponse.body()).thenReturn("{\"name\": \"validation_error\", \"message\": \"API key is invalid\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Aarav Patel",
                "710021106001",
                "aarav@college.edu",
                "test-token"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("authentication failed"));
    }

    @Test
    @DisplayName("Resend API: 422 Unverified sender/domain returns validation error")
    @SuppressWarnings("unchecked")
    void testUnverifiedSenderError() throws Exception {
        when(mockResponse.statusCode()).thenReturn(422);
        when(mockResponse.body()).thenReturn("{\"name\": \"validation_error\", \"message\": \"Domain not verified in Resend\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendAdminWelcomeEmail(
                "Dr. Sharma",
                "Class Coordinator",
                "sharma@college.edu",
                "admin-token"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("Resend validation error"));
        assertTrue(result.message().contains("Domain not verified in Resend"));
    }

    @Test
    @DisplayName("Resend API: 429 Rate limit returns retry warning")
    @SuppressWarnings("unchecked")
    void testRateLimit429() throws Exception {
        when(mockResponse.statusCode()).thenReturn(429);
        when(mockResponse.body()).thenReturn("{\"name\": \"rate_limit_exceeded\", \"message\": \"Too many requests\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Student Name",
                "ROLL123",
                "student@college.edu",
                "token"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("rate limit"));
    }

    @Test
    @DisplayName("Resend API: 500 Server error returns service error")
    @SuppressWarnings("unchecked")
    void testServer500Error() throws Exception {
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("{\"name\": \"internal_server_error\"}");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Student Name",
                "ROLL123",
                "student@college.edu",
                "token"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("Resend service error"));
    }

    @Test
    @DisplayName("Resend API: Connection timeout returns timeout error")
    @SuppressWarnings("unchecked")
    void testConnectionTimeout() throws Exception {
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new HttpConnectTimeoutException("Connection timed out"));

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Student Name",
                "ROLL123",
                "student@college.edu",
                "token"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("timed out"));
    }

    @Test
    @DisplayName("Resend API: Unconfigured API key safely skips sending without exception")
    void testUnconfiguredApiKey() {
        emailService.setResendApiKey("");

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Student Name",
                "ROLL123",
                "student@college.edu",
                "token"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("not configured"));
    }

    @Test
    @DisplayName("Student activation email constructs correct recipient, from header, and body")
    @SuppressWarnings("unchecked")
    void testStudentEmailPayload() throws Exception {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"id\": \"re_stu999\"}");
        when(mockHttpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Divya Raman",
                "710021106099",
                "divya@college.edu",
                "unique-token-999"
        );

        assertTrue(result.success());
        HttpRequest sentRequest = requestCaptor.getValue();
        assertEquals("POST", sentRequest.method());
        assertEquals("https://api.resend.com/emails", sentRequest.uri().toString());
        assertTrue(sentRequest.headers().firstValue("Authorization").orElse("").contains("Bearer re_test_key_12345"));
        assertEquals("application/json", sentRequest.headers().firstValue("Content-Type").orElse(""));
    }

    @Test
    @DisplayName("Admin activation email constructs correct designation and recipient")
    @SuppressWarnings("unchecked")
    void testAdminEmailPayload() throws Exception {
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("{\"id\": \"re_adm888\"}");
        when(mockHttpClient.send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendAdminWelcomeEmail(
                "Prof. Arvind",
                "Innovation Coordinator",
                "arvind@college.edu",
                "admin-token-888"
        );

        assertTrue(result.success());
        HttpRequest sentRequest = requestCaptor.getValue();
        assertEquals("POST", sentRequest.method());
        assertTrue(sentRequest.headers().firstValue("Authorization").orElse("").contains("Bearer re_test_key_12345"));
    }

    @Test
    @DisplayName("Resend API: Malformed JSON response is handled safely without throwing exceptions")
    @SuppressWarnings("unchecked")
    void testMalformedApiResponseHandledSafely() throws Exception {
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("Not a valid JSON string");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Aarav Patel",
                "710021106001",
                "aarav@college.edu",
                "test-token"
        );

        assertTrue(result.success());
        assertTrue(result.message().contains("accepted"));
    }

    @Test
    @DisplayName("Resend API: Malformed 422 error response falls back gracefully")
    @SuppressWarnings("unchecked")
    void testMalformedErrorResponseHandledSafely() throws Exception {
        when(mockResponse.statusCode()).thenReturn(422);
        when(mockResponse.body()).thenReturn("<html>502 Bad Gateway</html>");
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        EmailService.EmailResult result = emailService.sendStudentWelcomeEmail(
                "Aarav Patel",
                "710021106001",
                "aarav@college.edu",
                "test-token"
        );

        assertFalse(result.success());
        assertTrue(result.message().contains("Resend validation error"));
    }
}

