package com.finlab.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finlab.gateway.AbstractIntegrationTest;
import com.finlab.gateway.dto.LoginRequest;
import com.finlab.gateway.dto.LoginResponse;
import com.finlab.gateway.dto.RefreshRequest;
import com.finlab.gateway.exception.AuthenticationException;
import com.finlab.gateway.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void login_WithValidCredentials_ShouldReturn200WithTokens() throws Exception {
        // Arrange
        LoginRequest loginRequest = new LoginRequest("testuser", "password123");
        LoginResponse loginResponse = new LoginResponse("access-token", "refresh-token", 900000L);

        when(authService.login(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(loginResponse);

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(900000));

        verify(authService).login(eq("testuser"), eq("password123"), anyString(), anyString());
    }

    @Test
    void login_WithInvalidCredentials_ShouldReturn401() throws Exception {
        // Arrange
        LoginRequest loginRequest = new LoginRequest("testuser", "wrongpassword");

        when(authService.login(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new AuthenticationException("Invalid username or password"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void logout_WithValidToken_ShouldReturn200() throws Exception {
        // Arrange
        doNothing().when(authService).logout(anyString(), anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer valid-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("success"))
            .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authService).logout(eq("valid-token"), anyString(), anyString());
    }

    @Test
    void logout_WithoutToken_ShouldReturn400() throws Exception {
        // Act & Assert
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("error"))
            .andExpect(jsonPath("$.message").value("Authorization header is required"));

        verify(authService, never()).logout(anyString(), anyString(), anyString());
    }

    @Test
    void logout_WithInvalidToken_ShouldReturn401() throws Exception {
        // Arrange
        doThrow(new AuthenticationException("Invalid token"))
            .when(authService).logout(anyString(), anyString(), anyString());

        // Act & Assert
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Invalid token"));
    }

    @Test
    void refreshToken_WithValidRefreshToken_ShouldReturn200WithNewAccessToken() throws Exception {
        // Arrange
        RefreshRequest refreshRequest = new RefreshRequest("valid-refresh-token");
        LoginResponse loginResponse = new LoginResponse("new-access-token", "valid-refresh-token", 900000L);

        when(authService.refreshToken(anyString(), anyString(), anyString()))
            .thenReturn(loginResponse);

        // Act & Assert
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").value("new-access-token"))
            .andExpect(jsonPath("$.refreshToken").value("valid-refresh-token"))
            .andExpect(jsonPath("$.expiresIn").value(900000));

        verify(authService).refreshToken(eq("valid-refresh-token"), anyString(), anyString());
    }

    @Test
    void refreshToken_WithInvalidRefreshToken_ShouldReturn401() throws Exception {
        // Arrange
        RefreshRequest refreshRequest = new RefreshRequest("invalid-refresh-token");

        when(authService.refreshToken(anyString(), anyString(), anyString()))
            .thenThrow(new AuthenticationException("Invalid or expired refresh token"));

        // Act & Assert
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }
}
