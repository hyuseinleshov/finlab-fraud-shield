package com.finlab.gateway.service;

import com.finlab.gateway.repository.JwtTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    // JWT Configuration constants
    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256";
    private static final long EXPIRATION_MS = 900_000L; // 15 minutes
    private static final long REFRESH_EXPIRATION_MS = 604_800_000L; // 7 days
    private static final long SHORT_EXPIRATION_MS = 1L; // 1ms for expiration tests

    // Test data constants
    private static final Long TEST_USER_ID = 123L;
    private static final String TEST_USERNAME = "testuser";
    private static final String TEST_USERNAME_SIMPLE = "user123";

    // Token types (as stored in database - matches actual implementation)
    private static final String TOKEN_TYPE_ACCESS = "access";
    private static final String TOKEN_TYPE_REFRESH = "refresh";

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private JwtTokenRepository jwtTokenRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        jwtService = new JwtService(
                TEST_SECRET,
                EXPIRATION_MS,
                REFRESH_EXPIRATION_MS,
                redisTemplate,
                jwtTokenRepository
        );
    }

    @Test
    void generateToken_ShouldCreateValidToken() {
        String token = jwtService.generateToken(TEST_USER_ID, TEST_USERNAME);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        verify(valueOperations).set(anyString(), eq(TEST_USERNAME), any());
        verify(jwtTokenRepository).saveToken(eq(TEST_USER_ID), eq(token), any(Instant.class), eq(TOKEN_TYPE_ACCESS));
    }

    @Test
    void extractUserId_ShouldReturnCorrectUserId() {
        String token = jwtService.generateToken(TEST_USER_ID, TEST_USERNAME_SIMPLE);

        String extractedUserId = jwtService.extractUserId(token);

        assertEquals(TEST_USERNAME_SIMPLE, extractedUserId);
    }

    @Test
    void validateToken_WithValidToken_ShouldReturnTrue() {
        String token = jwtService.generateToken(TEST_USER_ID, TEST_USERNAME_SIMPLE);

        when(valueOperations.get(anyString())).thenReturn(TEST_USERNAME_SIMPLE);

        boolean isValid = jwtService.validateToken(token);

        assertTrue(isValid);
    }

    @Test
    void validateToken_WithInvalidToken_ShouldReturnFalse() {
        String invalidToken = "invalid.token.here";

        boolean isValid = jwtService.validateToken(invalidToken);

        assertFalse(isValid);
    }

    @Test
    void validateToken_WithExpiredToken_ShouldReturnFalse() throws InterruptedException {
        JwtService shortLivedJwtService = new JwtService(
                TEST_SECRET,
                SHORT_EXPIRATION_MS,
                REFRESH_EXPIRATION_MS,
                redisTemplate,
                jwtTokenRepository
        );

        String token = shortLivedJwtService.generateToken(TEST_USER_ID, TEST_USERNAME_SIMPLE);

        Thread.sleep(100); // Wait for token to expire

        boolean isValid = shortLivedJwtService.validateToken(token);

        assertFalse(isValid);
    }

    @Test
    void validateToken_WithBlacklistedToken_ShouldReturnFalse() {
        String token = jwtService.generateToken(TEST_USER_ID, TEST_USERNAME_SIMPLE);

        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        boolean isValid = jwtService.validateToken(token);

        assertFalse(isValid);
    }

    @Test
    void invalidateToken_ShouldBlacklistAndRemoveToken() {
        String token = jwtService.generateToken(TEST_USER_ID, TEST_USERNAME_SIMPLE);

        jwtService.invalidateToken(token);

        verify(valueOperations).set(contains("jwt:blacklist:"), eq("true"), any());
        verify(redisTemplate).delete(contains("jwt:token:"));
        verify(jwtTokenRepository).deleteToken(eq(TEST_USER_ID), eq(token));
    }

    @Test
    void isTokenExpired_WithValidToken_ShouldReturnFalse() {
        String token = jwtService.generateToken(TEST_USER_ID, TEST_USERNAME_SIMPLE);

        boolean isExpired = jwtService.isTokenExpired(token);

        assertFalse(isExpired);
    }

    @Test
    void generateRefreshToken_ShouldCreateValidToken() {
        String refreshToken = jwtService.generateRefreshToken(TEST_USER_ID, TEST_USERNAME_SIMPLE);

        assertNotNull(refreshToken);
        assertFalse(refreshToken.isEmpty());
        verify(valueOperations).set(anyString(), eq(TEST_USERNAME_SIMPLE), any());
        verify(jwtTokenRepository).saveToken(eq(TEST_USER_ID), eq(refreshToken), any(Instant.class), eq(TOKEN_TYPE_REFRESH));
    }

    @Test
    void validateToken_WithDatabaseFallback_ShouldReturnTrue() {
        String token = jwtService.generateToken(TEST_USER_ID, TEST_USERNAME_SIMPLE);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(jwtTokenRepository.tokenExists(eq(TEST_USER_ID), eq(token))).thenReturn(true);

        boolean isValid = jwtService.validateToken(token);

        assertTrue(isValid);
        verify(valueOperations, times(2)).set(anyString(), eq(TEST_USERNAME_SIMPLE), any());
    }
}
