package com.finlab.gateway.controller;

import com.finlab.gateway.dto.LoginRequest;
import com.finlab.gateway.dto.LoginResponse;
import com.finlab.gateway.dto.RefreshRequest;
import com.finlab.gateway.service.AuthService;
import com.finlab.gateway.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST controller for authentication endpoints.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Login endpoint.
     *
     * @param loginRequest contains username and password
     * @param request HTTP request for extracting IP and user agent
     * @return JWT access and refresh tokens
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String ipAddress = HttpRequestUtils.extractClientIp(request);
        String userAgent = HttpRequestUtils.extractUserAgent(request);

        log.info("Login request received for user: {}, IP: {}", loginRequest.getUsername(), ipAddress);

        LoginResponse response = authService.login(
            loginRequest.getUsername(),
            loginRequest.getPassword(),
            ipAddress,
            userAgent
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint.
     *
     * @param request HTTP request containing JWT token in Authorization header
     * @return success or error response
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpServletRequest request) {
        String token = extractToken(request);
        String ipAddress = HttpRequestUtils.extractClientIp(request);
        String userAgent = HttpRequestUtils.extractUserAgent(request);

        if (token == null) {
            log.warn("Logout request without token, IP: {}", ipAddress);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", "error");
            errorResponse.put("message", "Authorization header is required");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
        }

        authService.logout(token, ipAddress, userAgent);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        response.put("status", "success");

        return ResponseEntity.ok(response);
    }

    /**
     * Refresh token endpoint.
     *
     * @param refreshRequest contains refresh token
     * @param request HTTP request for extracting IP and user agent
     * @return new JWT access token
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refreshToken(@RequestBody RefreshRequest refreshRequest, HttpServletRequest request) {
        String ipAddress = HttpRequestUtils.extractClientIp(request);
        String userAgent = HttpRequestUtils.extractUserAgent(request);

        log.info("Token refresh request received, IP: {}", ipAddress);

        LoginResponse response = authService.refreshToken(
            refreshRequest.getRefreshToken(),
            ipAddress,
            userAgent
        );

        return ResponseEntity.ok(response);
    }

    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
