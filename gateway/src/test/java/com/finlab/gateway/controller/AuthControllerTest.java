package com.finlab.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finlab.gateway.config.BaseIntegrationTest;
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

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuthControllerTest extends BaseIntegrationTest {

    // Test credentials
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_PASSWORD = "password123";
    private static final String WRONG_PASSWORD = "wrongpassword";

    // Test tokens
    private static final String ACCESS_TOKEN = "access-token";
    private static final String REFRESH_TOKEN = "refresh-token";
    private static final String NEW_ACCESS_TOKEN = "new-access-token";
    private static final String VALID_TOKEN = "valid-token";
    private static final String INVALID_TOKEN = "invalid-token";
    private static final String VALID_REFRESH_TOKEN = "valid-refresh-token";
    private static final String INVALID_REFRESH_TOKEN = "invalid-refresh-token";

    // Token metadata
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final long JWT_EXPIRATION_MS = 900000L;

    // HTTP status codes
    private static final int HTTP_UNAUTHORIZED = 401;

    // API endpoints
    private static final String ENDPOINT_LOGIN = "/api/auth/login";
    private static final String ENDPOINT_LOGOUT = "/api/auth/logout";
    private static final String ENDPOINT_REFRESH = "/api/auth/refresh";

    // HTTP headers
    private static final String HEADER_AUTHORIZATION = "Authorization";

    // JSON path constants
    private static final String JSON_PATH_ACCESS_TOKEN = "$.accessToken";
    private static final String JSON_PATH_REFRESH_TOKEN = "$.refreshToken";
    private static final String JSON_PATH_TOKEN_TYPE = "$.tokenType";
    private static final String JSON_PATH_EXPIRES_IN = "$.expiresIn";
    private static final String JSON_PATH_STATUS = "$.status";
    private static final String JSON_PATH_ERROR = "$.error";
    private static final String JSON_PATH_MESSAGE = "$.message";

    // Response messages
    private static final String MSG_INVALID_CREDENTIALS = "Invalid username or password";
    private static final String MSG_INVALID_TOKEN = "Invalid token";
    private static final String MSG_INVALID_REFRESH_TOKEN = "Invalid or expired refresh token";
    private static final String MSG_LOGGED_OUT = "Logged out successfully";
    private static final String MSG_AUTH_HEADER_REQUIRED = "Authorization header is required";
    private static final String MSG_UNAUTHORIZED = "Unauthorized";
    private static final String STATUS_SUCCESS = "success";
    private static final String STATUS_ERROR = "error";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void login_WithValidCredentials_ShouldReturn200WithTokens() throws Exception {
        LoginRequest loginRequest = new LoginRequest(TEST_USERNAME, TEST_PASSWORD);
        LoginResponse loginResponse = new LoginResponse(ACCESS_TOKEN, REFRESH_TOKEN, JWT_EXPIRATION_MS);

        when(authService.login(anyString(), anyString(), anyString(), anyString()))
            .thenReturn(loginResponse);

        mockMvc.perform(post(ENDPOINT_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_PATH_ACCESS_TOKEN).value(ACCESS_TOKEN))
            .andExpect(jsonPath(JSON_PATH_REFRESH_TOKEN).value(REFRESH_TOKEN))
            .andExpect(jsonPath(JSON_PATH_TOKEN_TYPE).value(TOKEN_TYPE_BEARER))
            .andExpect(jsonPath(JSON_PATH_EXPIRES_IN).value(JWT_EXPIRATION_MS));

        verify(authService).login(eq(TEST_USERNAME), eq(TEST_PASSWORD), anyString(), anyString());
    }

    @Test
    void login_WithInvalidCredentials_ShouldReturn401() throws Exception {
        LoginRequest loginRequest = new LoginRequest(TEST_USERNAME, WRONG_PASSWORD);

        when(authService.login(anyString(), anyString(), anyString(), anyString()))
            .thenThrow(new AuthenticationException(MSG_INVALID_CREDENTIALS));

        mockMvc.perform(post(ENDPOINT_LOGIN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(HTTP_UNAUTHORIZED))
            .andExpect(jsonPath(JSON_PATH_ERROR).value(MSG_UNAUTHORIZED))
            .andExpect(jsonPath(JSON_PATH_MESSAGE).value(MSG_INVALID_CREDENTIALS));
    }

    @Test
    void logout_WithValidToken_ShouldReturn200() throws Exception {
        doNothing().when(authService).logout(anyString(), anyString(), anyString());

        mockMvc.perform(post(ENDPOINT_LOGOUT)
                .header(HEADER_AUTHORIZATION, TOKEN_TYPE_BEARER + " " + VALID_TOKEN))
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(STATUS_SUCCESS))
            .andExpect(jsonPath(JSON_PATH_MESSAGE).value(MSG_LOGGED_OUT));

        verify(authService).logout(eq(VALID_TOKEN), anyString(), anyString());
    }

    @Test
    void logout_WithoutToken_ShouldReturn400() throws Exception {
        mockMvc.perform(post(ENDPOINT_LOGOUT))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(STATUS_ERROR))
            .andExpect(jsonPath(JSON_PATH_MESSAGE).value(MSG_AUTH_HEADER_REQUIRED));

        verify(authService, never()).logout(anyString(), anyString(), anyString());
    }

    @Test
    void logout_WithInvalidToken_ShouldReturn401() throws Exception {
        doThrow(new AuthenticationException(MSG_INVALID_TOKEN))
            .when(authService).logout(anyString(), anyString(), anyString());

        mockMvc.perform(post(ENDPOINT_LOGOUT)
                .header(HEADER_AUTHORIZATION, TOKEN_TYPE_BEARER + " " + INVALID_TOKEN))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(HTTP_UNAUTHORIZED))
            .andExpect(jsonPath(JSON_PATH_MESSAGE).value(MSG_INVALID_TOKEN));
    }

    @Test
    void refreshToken_WithValidRefreshToken_ShouldReturn200WithNewAccessToken() throws Exception {
        RefreshRequest refreshRequest = new RefreshRequest(VALID_REFRESH_TOKEN);
        LoginResponse loginResponse = new LoginResponse(NEW_ACCESS_TOKEN, VALID_REFRESH_TOKEN, JWT_EXPIRATION_MS);

        when(authService.refreshToken(anyString(), anyString(), anyString()))
            .thenReturn(loginResponse);

        mockMvc.perform(post(ENDPOINT_REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_PATH_ACCESS_TOKEN).value(NEW_ACCESS_TOKEN))
            .andExpect(jsonPath(JSON_PATH_REFRESH_TOKEN).value(VALID_REFRESH_TOKEN))
            .andExpect(jsonPath(JSON_PATH_EXPIRES_IN).value(JWT_EXPIRATION_MS));

        verify(authService).refreshToken(eq(VALID_REFRESH_TOKEN), anyString(), anyString());
    }

    @Test
    void refreshToken_WithInvalidRefreshToken_ShouldReturn401() throws Exception {
        RefreshRequest refreshRequest = new RefreshRequest(INVALID_REFRESH_TOKEN);

        when(authService.refreshToken(anyString(), anyString(), anyString()))
            .thenThrow(new AuthenticationException(MSG_INVALID_REFRESH_TOKEN));

        mockMvc.perform(post(ENDPOINT_REFRESH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(HTTP_UNAUTHORIZED))
            .andExpect(jsonPath(JSON_PATH_MESSAGE).value(MSG_INVALID_REFRESH_TOKEN));
    }
}
