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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

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
        authService = new AuthService(jwtService, userRepository, auditLogRepository, passwordEncoder, 900000L);
    }

    @Test
    void login_WithValidCredentials_ShouldReturnJwtTokens() {
        // Arrange
        String username = "testuser";
        String password = "password123";
        String passwordHash = passwordEncoder.encode(password);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByUsername(username)).thenReturn(user);
        when(jwtService.generateToken(username)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(username)).thenReturn("refresh-token");

        // Act
        LoginResponse response = authService.login(username, password, "127.0.0.1", "TestAgent");

        // Assert
        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());
        assertEquals("Bearer", response.getTokenType());
        assertEquals(900000L, response.getExpiresIn());

        verify(userRepository).updateLastLogin(username);
        verify(auditLogRepository).logAuthEvent(eq(username), eq("LOGIN"), anyString(), anyString(), anyMap());
    }

    @Test
    void login_WithInvalidPassword_ShouldThrowAuthenticationException() {
        // Arrange
        String username = "testuser";
        String password = "wrongpassword";
        String passwordHash = passwordEncoder.encode("correctpassword");

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setLocked(false);
        user.setFailedLoginAttempts(0);

        when(userRepository.findByUsername(username)).thenReturn(user);

        // Act & Assert
        assertThrows(AuthenticationException.class, () ->
            authService.login(username, password, "127.0.0.1", "TestAgent")
        );

        verify(userRepository).incrementFailedLoginAttempts(username);
        verify(auditLogRepository).logAuthEvent(eq(username), eq("LOGIN_FAILED"), anyString(), anyString(), anyMap());
    }

    @Test
    void login_WithNonExistentUser_ShouldThrowAuthenticationException() {
        // Arrange
        String username = "nonexistent";
        String password = "password123";

        when(userRepository.findByUsername(username)).thenReturn(null);

        // Act & Assert
        assertThrows(AuthenticationException.class, () ->
            authService.login(username, password, "127.0.0.1", "TestAgent")
        );

        verify(auditLogRepository).logFailedAuthEvent(eq("LOGIN_FAILED"), anyString(), anyString(), anyMap());
    }

    @Test
    void login_WithInactiveAccount_ShouldThrowAuthenticationException() {
        // Arrange
        String username = "testuser";
        String password = "password123";
        String passwordHash = passwordEncoder.encode(password);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setActive(false);
        user.setLocked(false);

        when(userRepository.findByUsername(username)).thenReturn(user);

        // Act & Assert
        AuthenticationException exception = assertThrows(AuthenticationException.class, () ->
            authService.login(username, password, "127.0.0.1", "TestAgent")
        );

        assertEquals("Account is inactive", exception.getMessage());
        verify(auditLogRepository).logAuthEvent(eq(username), eq("LOGIN_FAILED"), anyString(), anyString(), anyMap());
    }

    @Test
    void login_WithLockedAccount_ShouldThrowAuthenticationException() {
        // Arrange
        String username = "testuser";
        String password = "password123";
        String passwordHash = passwordEncoder.encode(password);

        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordHash);
        user.setActive(true);
        user.setLocked(true);

        when(userRepository.findByUsername(username)).thenReturn(user);

        // Act & Assert
        AuthenticationException exception = assertThrows(AuthenticationException.class, () ->
            authService.login(username, password, "127.0.0.1", "TestAgent")
        );

        assertEquals("Account is locked", exception.getMessage());
        verify(auditLogRepository).logAuthEvent(eq(username), eq("LOGIN_FAILED"), anyString(), anyString(), anyMap());
    }

    @Test
    void logout_WithValidToken_ShouldInvalidateToken() {
        // Arrange
        String token = "valid-token";
        String userId = "testuser";

        when(jwtService.extractUserId(token)).thenReturn(userId);

        // Act
        authService.logout(token, "127.0.0.1", "TestAgent");

        // Assert
        verify(jwtService).invalidateToken(token);
        verify(auditLogRepository).logAuthEvent(eq(userId), eq("LOGOUT"), anyString(), anyString(), anyMap());
    }

    @Test
    void logout_WithInvalidToken_ShouldThrowAuthenticationException() {
        // Arrange
        String token = "invalid-token";

        when(jwtService.extractUserId(token)).thenReturn(null);

        // Act & Assert
        assertThrows(AuthenticationException.class, () ->
            authService.logout(token, "127.0.0.1", "TestAgent")
        );

        verify(jwtService, never()).invalidateToken(anyString());
    }

    @Test
    void refreshToken_WithValidRefreshToken_ShouldReturnNewAccessToken() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        String userId = "testuser";

        User user = new User();
        user.setUsername(userId);
        user.setActive(true);

        when(jwtService.validateToken(refreshToken)).thenReturn(true);
        when(jwtService.extractUserId(refreshToken)).thenReturn(userId);
        when(userRepository.findByUsername(userId)).thenReturn(user);
        when(jwtService.generateToken(userId)).thenReturn("new-access-token");

        // Act
        LoginResponse response = authService.refreshToken(refreshToken, "127.0.0.1", "TestAgent");

        // Assert
        assertNotNull(response);
        assertEquals("new-access-token", response.getAccessToken());
        assertEquals(refreshToken, response.getRefreshToken());

        verify(auditLogRepository).logAuthEvent(eq(userId), eq("REFRESH_TOKEN"), anyString(), anyString(), anyMap());
    }

    @Test
    void refreshToken_WithInvalidToken_ShouldThrowAuthenticationException() {
        // Arrange
        String refreshToken = "invalid-refresh-token";

        when(jwtService.validateToken(refreshToken)).thenReturn(false);

        // Act & Assert
        assertThrows(AuthenticationException.class, () ->
            authService.refreshToken(refreshToken, "127.0.0.1", "TestAgent")
        );

        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void refreshToken_WithInactiveUser_ShouldThrowAuthenticationException() {
        // Arrange
        String refreshToken = "valid-refresh-token";
        String userId = "testuser";

        User user = new User();
        user.setUsername(userId);
        user.setActive(false);

        when(jwtService.validateToken(refreshToken)).thenReturn(true);
        when(jwtService.extractUserId(refreshToken)).thenReturn(userId);
        when(userRepository.findByUsername(userId)).thenReturn(user);

        // Act & Assert
        assertThrows(AuthenticationException.class, () ->
            authService.refreshToken(refreshToken, "127.0.0.1", "TestAgent")
        );

        verify(jwtService, never()).generateToken(anyString());
    }
}
