package com.finlab.gateway.service;

import com.finlab.gateway.exception.AuthenticationException;
import com.finlab.gateway.dto.LoginResponse;
import com.finlab.gateway.model.User;
import com.finlab.gateway.repository.AuditLogRepository;
import com.finlab.gateway.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Authentication service handling login, logout, and token refresh operations.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final long jwtExpirationMs;

    public AuthService(
            JwtService jwtService,
            UserRepository userRepository,
            AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder,
            @Value("${jwt.expiration-ms:900000}") long jwtExpirationMs) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtExpirationMs = jwtExpirationMs;
    }

    /**
     * Authenticates user and generates JWT tokens.
     */
    public LoginResponse login(String username, String password, String ipAddress, String userAgent) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Username is required");
        }

        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password is required");
        }

        User user = userRepository.findByUsername(username);

        if (user == null) {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("reason", "user_not_found");
            auditLogRepository.logFailedAuthEvent("LOGIN_FAILED", ipAddress, userAgent, details);

            log.warn("Login failed - user not found: {}", username);
            throw new AuthenticationException("Invalid username or password");
        }

        if (!user.isActive()) {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("reason", "account_inactive");
            auditLogRepository.logAuthEvent(username, "LOGIN_FAILED", ipAddress, userAgent, details);

            log.warn("Login failed - account inactive: {}", username);
            throw new AuthenticationException("Account is inactive");
        }

        if (user.isLocked()) {
            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("reason", "account_locked");
            auditLogRepository.logAuthEvent(username, "LOGIN_FAILED", ipAddress, userAgent, details);

            log.warn("Login failed - account locked: {}", username);
            throw new AuthenticationException("Account is locked");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            userRepository.incrementFailedLoginAttempts(username);

            Map<String, Object> details = new HashMap<>();
            details.put("username", username);
            details.put("reason", "invalid_password");
            details.put("failed_attempts", user.getFailedLoginAttempts() + 1);
            auditLogRepository.logAuthEvent(username, "LOGIN_FAILED", ipAddress, userAgent, details);

            log.warn("Login failed - invalid password: {}", username);
            throw new AuthenticationException("Invalid username or password");
        }

        // Generate JWT tokens
        String accessToken = jwtService.generateToken(username);
        String refreshToken = jwtService.generateRefreshToken(username);

        // Update last login
        userRepository.updateLastLogin(username);

        // Log successful login
        Map<String, Object> details = new HashMap<>();
        details.put("method", "password");
        details.put("success", true);
        auditLogRepository.logAuthEvent(username, "LOGIN", ipAddress, userAgent, details);

        log.info("User logged in successfully: {}", username);

        return new LoginResponse(accessToken, refreshToken, jwtExpirationMs);
    }

    /**
     * Logs out user by invalidating the access token.
     */
    public void logout(String token, String ipAddress, String userAgent) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token is required");
        }

        String userId = jwtService.extractUserId(token);

        if (userId == null) {
            log.warn("Logout failed - invalid token");
            throw new AuthenticationException("Invalid token");
        }

        // Invalidate token (blacklist and remove from storage)
        jwtService.invalidateToken(token);

        // Log logout event
        Map<String, Object> details = new HashMap<>();
        details.put("method", "token_invalidation");
        auditLogRepository.logAuthEvent(userId, "LOGOUT", ipAddress, userAgent, details);

        log.info("User logged out successfully: {}", userId);
    }

    /**
     * Refreshes access token using refresh token.
     */
    public LoginResponse refreshToken(String refreshToken, String ipAddress, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        if (!jwtService.validateToken(refreshToken)) {
            log.warn("Token refresh failed - invalid refresh token");
            throw new AuthenticationException("Invalid or expired refresh token");
        }

        String userId = jwtService.extractUserId(refreshToken);

        if (userId == null) {
            log.warn("Token refresh failed - could not extract user ID");
            throw new AuthenticationException("Invalid refresh token");
        }

        // Verify user still exists and is active
        User user = userRepository.findByUsername(userId);

        if (user == null || !user.isActive()) {
            log.warn("Token refresh failed - user not found or inactive: {}", userId);
            throw new AuthenticationException("User account is no longer valid");
        }

        // Generate new access token
        String newAccessToken = jwtService.generateToken(userId);

        // Log token refresh
        Map<String, Object> details = new HashMap<>();
        details.put("method", "refresh_token");
        auditLogRepository.logAuthEvent(userId, "REFRESH_TOKEN", ipAddress, userAgent, details);

        log.info("Token refreshed successfully for user: {}", userId);

        // Return new access token with same refresh token
        return new LoginResponse(newAccessToken, refreshToken, jwtExpirationMs);
    }
}
