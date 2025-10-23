package com.finlab.gateway.service;

import com.finlab.gateway.exception.AuthenticationException;
import com.finlab.gateway.dto.LoginResponse;
import com.finlab.gateway.model.User;
import com.finlab.gateway.repository.AuditLogRepository;
import com.finlab.gateway.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // Test user data constants
    private static final Long TEST_USER_ID = 123L;
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String WRONG_PASSWORD = "wrongpassword";
    private static final String CORRECT_PASSWORD = "correctpassword";
    private static final String NONEXISTENT_USERNAME = "nonexistent";

    // Test tokens
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String VALID_REFRESH_TOKEN = "valid-refresh-token";
    private static final String INVALID_REFRESH_TOKEN = "invalid-refresh-token";
    private static final String VALID_TOKEN = "valid-token";
    private static final String INVALID_TOKEN = "invalid-token";
    private static final String TOKEN_TYPE_BEARER = "Bearer";

    // Auth actions
    private static final String AUTH_ACTION_LOGIN = "LOGIN";
    private static final String AUTH_ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    private static final String AUTH_ACTION_LOGOUT = "LOGOUT";
    private static final String AUTH_ACTION_REFRESH_TOKEN = "REFRESH_TOKEN";

    // Network constants
    private static final String TEST_IP_ADDRESS = "127.0.0.1";
    private static final String TEST_USER_AGENT = "TestAgent";

    // JWT expiration
    private static final long JWT_EXPIRATION_MS = 900_000L; // 15 minutes

    // Account status messages
    private static final String MSG_ACCOUNT_INACTIVE = "Account is inactive";
    private static final String MSG_ACCOUNT_LOCKED = "Account is locked";

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(jwtService, userRepository, auditLogRepository, passwordEncoder, JWT_EXPIRATION_MS);
    }

    @Test
    void login_WithValidCredentials_ShouldReturnJwtTokens() {
        String passwordHash = passwordEncoder.encode(TEST_PASSWORD);

        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(user);
        when(jwtService.generateToken(TEST_USER_ID, TEST_USERNAME)).thenReturn(ACCESS_TOKEN);
        when(jwtService.generateRefreshToken(TEST_USER_ID, TEST_USERNAME)).thenReturn(REFRESH_TOKEN);

        LoginResponse response = authService.login(TEST_USERNAME, TEST_PASSWORD, TEST_IP_ADDRESS, TEST_USER_AGENT);

        assertNotNull(response);
        assertEquals(ACCESS_TOKEN, response.getAccessToken());
        assertEquals(REFRESH_TOKEN, response.getRefreshToken());
        assertEquals(TOKEN_TYPE_BEARER, response.getTokenType());
        assertEquals(JWT_EXPIRATION_MS, response.getExpiresIn());

        verify(userRepository).updateLastLogin(TEST_USERNAME);
        verify(auditLogRepository).logAuthEvent(eq(TEST_USERNAME), eq(AUTH_ACTION_LOGIN), anyString(), anyString(), anyMap());
    }

    @Test
    void login_WithInvalidPassword_ShouldThrowAuthenticationException() {
        String passwordHash = passwordEncoder.encode(CORRECT_PASSWORD);

        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(user);

        assertThrows(AuthenticationException.class, () ->
            authService.login(TEST_USERNAME, WRONG_PASSWORD, TEST_IP_ADDRESS, TEST_USER_AGENT)
        );

        verify(userRepository).incrementFailedLoginAttempts(TEST_USERNAME);
        verify(auditLogRepository).logAuthEvent(eq(TEST_USERNAME), eq(AUTH_ACTION_LOGIN_FAILED), anyString(), anyString(), anyMap());
    }

    @Test
    void login_WithNonExistentUser_ShouldThrowAuthenticationException() {
        when(userRepository.findByUsername(NONEXISTENT_USERNAME)).thenReturn(null);

        assertThrows(AuthenticationException.class, () ->
            authService.login(NONEXISTENT_USERNAME, TEST_PASSWORD, TEST_IP_ADDRESS, TEST_USER_AGENT)
        );

        verify(auditLogRepository).logFailedAuthEvent(eq(AUTH_ACTION_LOGIN_FAILED), anyString(), anyString(), anyMap());
    }

    @Test
    void login_WithInactiveAccount_ShouldThrowAuthenticationException() {
        String passwordHash = passwordEncoder.encode(TEST_PASSWORD);

        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordHash);
        user.setActive(false);
        user.setLocked(false);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(user);

        AuthenticationException exception = assertThrows(AuthenticationException.class, () ->
            authService.login(TEST_USERNAME, TEST_PASSWORD, TEST_IP_ADDRESS, TEST_USER_AGENT)
        );

        assertEquals(MSG_ACCOUNT_INACTIVE, exception.getMessage());
        verify(auditLogRepository).logAuthEvent(eq(TEST_USERNAME), eq(AUTH_ACTION_LOGIN_FAILED), anyString(), anyString(), anyMap());
    }

    @Test
    void login_WithLockedAccount_ShouldThrowAuthenticationException() {
        String passwordHash = passwordEncoder.encode(TEST_PASSWORD);

        User user = new User();
        user.setUsername(TEST_USERNAME);
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setLocked(true);

        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(user);

        AuthenticationException exception = assertThrows(AuthenticationException.class, () ->
            authService.login(TEST_USERNAME, TEST_PASSWORD, TEST_IP_ADDRESS, TEST_USER_AGENT)
        );

        assertEquals(MSG_ACCOUNT_LOCKED, exception.getMessage());
        verify(auditLogRepository).logAuthEvent(eq(TEST_USERNAME), eq(AUTH_ACTION_LOGIN_FAILED), anyString(), anyString(), anyMap());
    }

    @Test
    void logout_WithValidToken_ShouldInvalidateToken() {
        when(jwtService.extractUserId(VALID_TOKEN)).thenReturn(TEST_USERNAME);

        authService.logout(VALID_TOKEN, TEST_IP_ADDRESS, TEST_USER_AGENT);

        verify(jwtService).invalidateToken(VALID_TOKEN);
        verify(auditLogRepository).logAuthEvent(eq(TEST_USERNAME), eq(AUTH_ACTION_LOGOUT), anyString(), anyString(), anyMap());
    }

    @Test
    void logout_WithInvalidToken_ShouldThrowAuthenticationException() {
        when(jwtService.extractUserId(INVALID_TOKEN)).thenReturn(null);

        assertThrows(AuthenticationException.class, () ->
            authService.logout(INVALID_TOKEN, TEST_IP_ADDRESS, TEST_USER_AGENT)
        );

        verify(jwtService, never()).invalidateToken(anyString());
    }

    @Test
    void refreshToken_WithValidRefreshToken_ShouldReturnNewAccessToken() {
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setActive(true);

        when(jwtService.validateToken(VALID_REFRESH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserId(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(user);
        when(jwtService.generateToken(TEST_USER_ID, TEST_USERNAME)).thenReturn(NEW_ACCESS_TOKEN);

        LoginResponse response = authService.refreshToken(VALID_REFRESH_TOKEN, TEST_IP_ADDRESS, TEST_USER_AGENT);

        assertNotNull(response);
        assertEquals(NEW_ACCESS_TOKEN, response.getAccessToken());
        assertEquals(VALID_REFRESH_TOKEN, response.getRefreshToken());

        verify(auditLogRepository).logAuthEvent(eq(TEST_USERNAME), eq(AUTH_ACTION_REFRESH_TOKEN), anyString(), anyString(), anyMap());
    }

    @Test
    void refreshToken_WithInvalidToken_ShouldThrowAuthenticationException() {
        when(jwtService.validateToken(INVALID_REFRESH_TOKEN)).thenReturn(false);

        assertThrows(AuthenticationException.class, () ->
            authService.refreshToken(INVALID_REFRESH_TOKEN, TEST_IP_ADDRESS, TEST_USER_AGENT)
        );

        verify(jwtService, never()).generateToken(anyLong(), anyString());
    }

    @Test
    void refreshToken_WithInactiveUser_ShouldThrowAuthenticationException() {
        User user = new User();
        user.setId(TEST_USER_ID);
        user.setUsername(TEST_USERNAME);
        user.setActive(false);

        when(jwtService.validateToken(VALID_REFRESH_TOKEN)).thenReturn(true);
        when(jwtService.extractUserId(VALID_REFRESH_TOKEN)).thenReturn(TEST_USERNAME);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(user);

        assertThrows(AuthenticationException.class, () ->
            authService.refreshToken(VALID_REFRESH_TOKEN, TEST_IP_ADDRESS, TEST_USER_AGENT)
        );

        verify(jwtService, never()).generateToken(anyLong(), anyString());
    }
}
