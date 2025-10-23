package com.finlab.gateway.repository;

import com.finlab.gateway.config.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenRepositoryTest extends BaseIntegrationTest {

    // Token type constants
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    // Time constants (in seconds)
    private static final long EXPIRATION_900_SECONDS = 900L;
    private static final long EXPIRATION_1800_SECONDS = 1800L;
    private static final long EXPIRED_100_SECONDS_AGO = 100L;

    // SQL queries
    private static final String SQL_DELETE_ALL_TOKENS = "DELETE FROM jwt_tokens";
    private static final String SQL_DELETE_TEST_USERS = "DELETE FROM users WHERE username LIKE 'testuser_%'";
    private static final String SQL_INSERT_USER = """
        INSERT INTO users (username, email, password_hash, full_name, is_active, is_locked, failed_login_attempts)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        RETURNING id
        """;
    private static final String SQL_COUNT_BY_TOKEN = "SELECT COUNT(*) FROM jwt_tokens WHERE token = ?";
    private static final String SQL_SELECT_USER_ID_BY_TOKEN = "SELECT user_id FROM jwt_tokens WHERE token = ?";
    private static final String SQL_COUNT_BY_USER_ID = "SELECT COUNT(*) FROM jwt_tokens WHERE user_id = ?";

    // Token prefix for test data
    private static final String TOKEN_PREFIX = "token_";
    private static final String NON_EXISTENT_TOKEN = "non_existent_token";

    @Autowired
    private JwtTokenRepository jwtTokenRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long testUserId;
    private String testUsername;
    private String testToken;
    private Long wrongUserId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(SQL_DELETE_ALL_TOKENS);
        jdbcTemplate.execute(SQL_DELETE_TEST_USERS);

        long timestamp = System.currentTimeMillis();
        testUsername = "testuser_" + timestamp;
        testToken = TOKEN_PREFIX + timestamp;

        testUserId = jdbcTemplate.queryForObject(
            SQL_INSERT_USER,
            Long.class,
            testUsername,
            testUsername + "@test.com",
            "$2a$10$dummyhash",
            "Test User",
            true,
            false,
            0
        );

        wrongUserId = jdbcTemplate.queryForObject(
            SQL_INSERT_USER,
            Long.class,
            "wrong_" + timestamp,
            "wrong_" + timestamp + "@test.com",
            "$2a$10$dummyhash",
            "Wrong User",
            true,
            false,
            0
        );
    }

    @Test
    void saveToken_WithValidData_ShouldSaveSuccessfully() {
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_900_SECONDS);

        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, TOKEN_TYPE_ACCESS);

        Integer count = jdbcTemplate.queryForObject(
                SQL_COUNT_BY_TOKEN,
                Integer.class,
                testToken
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void saveToken_WithDuplicateToken_ShouldUpdateExpiresAt() {
        Instant firstExpiry = Instant.now().plusSeconds(EXPIRATION_900_SECONDS);
        Instant secondExpiry = Instant.now().plusSeconds(EXPIRATION_1800_SECONDS);

        jwtTokenRepository.saveToken(testUserId, testToken, firstExpiry, TOKEN_TYPE_ACCESS);
        jwtTokenRepository.saveToken(testUserId, testToken, secondExpiry, TOKEN_TYPE_ACCESS);

        Integer count = jdbcTemplate.queryForObject(
                SQL_COUNT_BY_TOKEN,
                Integer.class,
                testToken
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void tokenExists_WithValidToken_ShouldReturnTrue() {
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_900_SECONDS);
        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, TOKEN_TYPE_ACCESS);

        boolean exists = jwtTokenRepository.tokenExists(testUserId, testToken);

        assertThat(exists).isTrue();
    }

    @Test
    void tokenExists_WithExpiredToken_ShouldReturnFalse() {
        Instant expiresAt = Instant.now().minusSeconds(EXPIRED_100_SECONDS_AGO);
        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, TOKEN_TYPE_ACCESS);

        boolean exists = jwtTokenRepository.tokenExists(testUserId, testToken);

        assertThat(exists).isFalse();
    }

    @Test
    void tokenExists_WithNonExistentToken_ShouldReturnFalse() {
        boolean exists = jwtTokenRepository.tokenExists(testUserId, NON_EXISTENT_TOKEN);

        assertThat(exists).isFalse();
    }

    @Test
    void tokenExists_WithWrongUserId_ShouldReturnFalse() {
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_900_SECONDS);
        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, TOKEN_TYPE_ACCESS);

        boolean exists = jwtTokenRepository.tokenExists(wrongUserId, testToken);

        assertThat(exists).isFalse();
    }

    @Test
    void deleteToken_ShouldRemoveToken() {
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_900_SECONDS);
        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, TOKEN_TYPE_ACCESS);

        jwtTokenRepository.deleteToken(testUserId, testToken);

        boolean exists = jwtTokenRepository.tokenExists(testUserId, testToken);
        assertThat(exists).isFalse();

        Integer count = jdbcTemplate.queryForObject(
                SQL_COUNT_BY_TOKEN,
                Integer.class,
                testToken
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    void deleteToken_WithNonExistentToken_ShouldNotThrowException() {
        jwtTokenRepository.deleteToken(testUserId, NON_EXISTENT_TOKEN);

        Integer count = jdbcTemplate.queryForObject(
                SQL_COUNT_BY_USER_ID,
                Integer.class,
                testUserId
        );
        assertThat(count).isEqualTo(0);
    }

    @Test
    void saveToken_ShouldStoreUserIdAndExpiresAt() {
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_900_SECONDS);

        jwtTokenRepository.saveToken(testUserId, testToken, expiresAt, TOKEN_TYPE_ACCESS);

        Long storedUserId = jdbcTemplate.queryForObject(
                SQL_SELECT_USER_ID_BY_TOKEN,
                Long.class,
                testToken
        );

        assertThat(storedUserId).isEqualTo(testUserId);
    }

    @Test
    void tokenExists_WithMultipleTokensForUser_ShouldReturnCorrectResult() {
        String token1 = TOKEN_PREFIX + "1_" + System.currentTimeMillis();
        String token2 = TOKEN_PREFIX + "2_" + System.currentTimeMillis();
        Instant expiresAt = Instant.now().plusSeconds(EXPIRATION_900_SECONDS);

        jwtTokenRepository.saveToken(testUserId, token1, expiresAt, TOKEN_TYPE_ACCESS);
        jwtTokenRepository.saveToken(testUserId, token2, expiresAt, TOKEN_TYPE_ACCESS);

        assertThat(jwtTokenRepository.tokenExists(testUserId, token1)).isTrue();
        assertThat(jwtTokenRepository.tokenExists(testUserId, token2)).isTrue();
    }
}
