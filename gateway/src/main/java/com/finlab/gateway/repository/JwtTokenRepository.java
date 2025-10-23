package com.finlab.gateway.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;

@Repository
@Transactional(readOnly = true)
public class JwtTokenRepository {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public JwtTokenRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void saveToken(String userId, String token, Instant expiresAt, String tokenType) {
        String sql = """
            INSERT INTO jwt_tokens (user_id, token, token_type, expires_at, created_at)
            VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT (token) DO UPDATE
            SET expires_at = EXCLUDED.expires_at, token_type = EXCLUDED.token_type
            """;

        int[] types = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.TIMESTAMP};

        jdbcTemplate.update(sql,
            new Object[]{userId, token, tokenType.toUpperCase(), Timestamp.from(expiresAt)},
            types);

        log.debug("Saved {} JWT token for user: {}", tokenType, userId);
    }

    public boolean tokenExists(String userId, String token) {
        String sql = """
            SELECT COUNT(*) FROM jwt_tokens
            WHERE user_id = ? AND token = ? AND expires_at > CURRENT_TIMESTAMP
            """;

        int[] types = {Types.VARCHAR, Types.VARCHAR};

        try {
            Integer count = jdbcTemplate.queryForObject(sql,
                new Object[]{userId, token},
                types,
                Integer.class);

            return count != null && count > 0;
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    @Transactional
    public void deleteToken(String userId, String token) {
        String sql = "DELETE FROM jwt_tokens WHERE user_id = ? AND token = ?";
        int[] types = {Types.VARCHAR, Types.VARCHAR};

        int deleted = jdbcTemplate.update(sql,
            new Object[]{userId, token},
            types);

        log.debug("Deleted {} token(s) for user: {}", deleted, userId);
    }
}
