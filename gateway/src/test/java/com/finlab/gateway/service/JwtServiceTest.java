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

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private JwtTokenRepository jwtTokenRepository;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private JwtService jwtService;

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-256-bits-long-for-hmac-sha256";
    private static final long EXPIRATION_MS = 900000L; // 15 minutes
    private static final long REFRESH_EXPIRATION_MS = 604800000L; // 7 days

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
        Long userId = 123L;
        String username = "testuser";

        String token = jwtService.generateToken(userId, username);

        assertNotNull(token);
        assertFalse(token.isEmpty());
        verify(valueOperations).set(anyString(), eq(username), any());
        verify(jwtTokenRepository).saveToken(eq(userId), eq(token), any(Instant.class), eq("ACCESS"));
    }

    @Test
    void extractUserId_ShouldReturnCorrectUserId() {
        Long userId = 123L;
        String username = "user123";
        String token = jwtService.generateToken(userId, username);

        String extractedUserId = jwtService.extractUserId(token);

        assertEquals(username, extractedUserId);
    }

    @Test
    void validateToken_WithValidToken_ShouldReturnTrue() {
        Long userId = 123L;
        String username = "user123";
        String token = jwtService.generateToken(userId, username);

        when(valueOperations.get(anyString())).thenReturn(username);

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
                1L, // 1ms expiration
                REFRESH_EXPIRATION_MS,
                redisTemplate,
                jwtTokenRepository
        );

        Long userId = 123L;
        String username = "user123";
        String token = shortLivedJwtService.generateToken(userId, username);

        Thread.sleep(100); // Wait for token to expire

        boolean isValid = shortLivedJwtService.validateToken(token);

        assertFalse(isValid);
    }

    @Test
    void validateToken_WithBlacklistedToken_ShouldReturnFalse() {
        Long userId = 123L;
        String username = "user123";
        String token = jwtService.generateToken(userId, username);

        when(redisTemplate.hasKey(anyString())).thenReturn(true);

        boolean isValid = jwtService.validateToken(token);

        assertFalse(isValid);
    }

    @Test
    void invalidateToken_ShouldBlacklistAndRemoveToken() {
        Long userId = 123L;
        String username = "user123";
        String token = jwtService.generateToken(userId, username);

        jwtService.invalidateToken(token);

        verify(valueOperations).set(contains("jwt:blacklist:"), eq("true"), any());
        verify(redisTemplate).delete(contains("jwt:token:"));
        verify(jwtTokenRepository).deleteToken(eq(username), eq(token));
    }

    @Test
    void isTokenExpired_WithValidToken_ShouldReturnFalse() {
        Long userId = 123L;
        String username = "user123";
        String token = jwtService.generateToken(userId, username);

        boolean isExpired = jwtService.isTokenExpired(token);

        assertFalse(isExpired);
    }

    @Test
    void generateRefreshToken_ShouldCreateValidToken() {
        Long userId = 123L;
        String username = "user123";

        String refreshToken = jwtService.generateRefreshToken(userId, username);

        assertNotNull(refreshToken);
        assertFalse(refreshToken.isEmpty());
        verify(valueOperations).set(anyString(), eq(username), any());
        verify(jwtTokenRepository).saveToken(eq(userId), eq(refreshToken), any(Instant.class), eq("REFRESH"));
    }

    @Test
    void validateToken_WithDatabaseFallback_ShouldReturnTrue() {
        Long userId = 123L;
        String username = "user123";
        String token = jwtService.generateToken(userId, username);

        when(valueOperations.get(anyString())).thenReturn(null);
        when(jwtTokenRepository.tokenExists(eq(username), eq(token))).thenReturn(true);

        boolean isValid = jwtService.validateToken(token);

        assertTrue(isValid);
        verify(valueOperations, times(2)).set(anyString(), eq(username), any());
    }
}
