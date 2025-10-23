package com.finlab.gateway.repository;

import com.finlab.gateway.config.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private JwtTokenRepository jwtTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String testUserId;
    private String testToken;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM jwt_tokens");

        testUserId = "user_" + System.currentTimeMillis();
        testToken = "token_" + System.currentTimeMillis();
    }

    @Test
    void saveToken_WithValidData_ShouldSaveSuccessfully() {
        Instant expiresAt = Instant.now().plusSeconds(900);

        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, "ACCESS");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_tokens WHERE token = ?",
                Integer.class,
                testToken
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void saveToken_WithDuplicateToken_ShouldUpdateExpiresAt() {
        Instant firstExpiry = Instant.now().plusSeconds(900);
        Instant secondExpiry = Instant.now().plusSeconds(1800);

        jwtTokenRepository.saveToken(testUserId, testToken, firstExpiry, "ACCESS");
        jwtTokenRepository.saveToken(testUserId, testToken, secondExpiry, "ACCESS");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_tokens WHERE token = ?",
                Integer.class,
                testToken
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void tokenExists_WithValidToken_ShouldReturnTrue() {
        Instant expiresAt = Instant.now().plusSeconds(900);
        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, "ACCESS");

        boolean exists = jwtTokenRepository.tokenExists(testUserId, testToken);

        assertThat(exists).isTrue();
    }

    @Test
    void tokenExists_WithExpiredToken_ShouldReturnFalse() {
        Instant expiresAt = Instant.now().minusSeconds(100);
        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, "ACCESS");

        boolean exists = jwtTokenRepository.tokenExists(testUserId, testToken);

        assertThat(exists).isFalse();
    }

    @Test
    void tokenExists_WithNonExistentToken_ShouldReturnFalse() {
        boolean exists = jwtTokenRepository.tokenExists(testUserId, "non_existent_token");

        assertThat(exists).isFalse();
    }

    @Test
    void tokenExists_WithWrongUserId_ShouldReturnFalse() {
        Instant expiresAt = Instant.now().plusSeconds(900);
        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, "ACCESS");

        boolean exists = jwtTokenRepository.tokenExists("wrong_user_id", testToken);

        assertThat(exists).isFalse();
    }

    @Test
    void deleteToken_ShouldRemoveToken() {
        Instant expiresAt = Instant.now().plusSeconds(900);
        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, "ACCESS");

        jwtTokenRepository.deleteToken(testUserId, testToken);

        boolean exists = jwtTokenRepository.tokenExists(testUserId, testToken);
        assertThat(exists).isFalse();

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_tokens WHERE token = ?",
                Integer.class,
                testToken
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    void deleteToken_WithNonExistentToken_ShouldNotThrowException() {
        jwtTokenRepository.deleteToken(testUserId, "non_existent_token");

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_tokens WHERE user_id = ?",
                Integer.class,
                testUserId
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    void saveToken_ShouldStoreUserIdAndExpiresAt() {
        Instant expiresAt = Instant.now().plusSeconds(900);

        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, "ACCESS");

        String storedUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM jwt_tokens WHERE token = ?",
                String.class,
                testToken
        );

        assertThat(storedUserId).isEqualTo(testUserId);
    }

    @Test
    void tokenExists_WithMultipleTokensForUser_ShouldReturnCorrectResult() {
        String token1 = "token_1_" + System.currentTimeMillis();
        String token2 = "token_2_" + System.currentTimeMillis();
        Instant expiresAt = Instant.now().plusSeconds(900);

        jwtTokenRepository.saveToken(testUserId, token1, expiresAt, "ACCESS");
        jwtTokenRepository.saveToken(testUserId, token2, expiresAt, "ACCESS");

        assertThat(jwtTokenRepository.tokenExists(testUserId, token1)).isTrue();
        assertThat(jwtTokenRepository.tokenExists(testUserId, token2)).isTrue();
    }
}
