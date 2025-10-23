package com.finlab.gateway.repository;

import com.finlab.gateway.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

@Repository
public class UserRepository {

    private static final Logger log = LoggerFactory.getLogger(UserRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<User> userRowMapper;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRowMapper = new UserRowMapper();
    }

    /**
     * Finds a user by username.
     */
    public User findByUsername(String username) {
        String sql = """
            SELECT id, username, email, password_hash, full_name, is_active, is_locked,
                   failed_login_attempts, last_login_at, created_at, updated_at
            FROM users
            WHERE username = ?
            """;

        int[] types = {Types.VARCHAR};

        try {
            User user = jdbcTemplate.queryForObject(sql,
                new Object[]{username},
                types,
                userRowMapper);

            log.debug("Found user: {}", username);
            return user;

        } catch (EmptyResultDataAccessException e) {
            log.debug("User not found: {}", username);
            return null;
        }
    }

    /**
     * Finds a user by email.
     */
    public User findByEmail(String email) {
        String sql = """
            SELECT id, username, email, password_hash, full_name, is_active, is_locked,
                   failed_login_attempts, last_login_at, created_at, updated_at
            FROM users
            WHERE email = ?
            """;

        int[] types = {Types.VARCHAR};

        try {
            User user = jdbcTemplate.queryForObject(sql,
                new Object[]{email},
                types,
                userRowMapper);

            log.debug("Found user by email: {}", email);
            return user;

        } catch (EmptyResultDataAccessException e) {
            log.debug("User not found by email: {}", email);
            return null;
        }
    }

    /**
     * Updates last login timestamp for a user.
     */
    public void updateLastLogin(String username) {
        String sql = """
            UPDATE users
            SET last_login_at = CURRENT_TIMESTAMP,
                failed_login_attempts = 0
            WHERE username = ?
            """;

        int[] types = {Types.VARCHAR};

        jdbcTemplate.update(sql,
            new Object[]{username},
            types);

        log.debug("Updated last login for user: {}", username);
    }

    /**
     * Increments failed login attempts for a user.
     */
    public void incrementFailedLoginAttempts(String username) {
        String sql = """
            UPDATE users
            SET failed_login_attempts = failed_login_attempts + 1
            WHERE username = ?
            """;

        int[] types = {Types.VARCHAR};

        jdbcTemplate.update(sql,
            new Object[]{username},
            types);

        log.debug("Incremented failed login attempts for user: {}", username);
    }

    /**
     * Inserts a new user into the database.
     */
    public void save(User user) {
        String sql = """
            INSERT INTO users (username, email, password_hash, full_name, is_active, is_locked, failed_login_attempts)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        int[] types = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR,
                       Types.BOOLEAN, Types.BOOLEAN, Types.INTEGER};

        jdbcTemplate.update(sql,
            new Object[]{
                user.getUsername(),
                user.getEmail(),
                user.getPasswordHash(),
                user.getFullName(),
                user.isActive(),
                user.isLocked(),
                user.getFailedLoginAttempts()
            },
            types);

        log.info("Inserted new user: {}", user.getUsername());
    }

    /**
     * RowMapper for User entity.
     */
    private static class UserRowMapper implements RowMapper<User> {
        @Override
        public User mapRow(ResultSet rs, int rowNum) throws SQLException {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            user.setPasswordHash(rs.getString("password_hash"));
            user.setFullName(rs.getString("full_name"));
            user.setActive(rs.getBoolean("is_active"));
            user.setLocked(rs.getBoolean("is_locked"));
            user.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));

            var lastLoginAt = rs.getTimestamp("last_login_at");
            if (lastLoginAt != null) {
                user.setLastLoginAt(lastLoginAt.toInstant());
            }

            var createdAt = rs.getTimestamp("created_at");
            if (createdAt != null) {
                user.setCreatedAt(createdAt.toInstant());
            }

            var updatedAt = rs.getTimestamp("updated_at");
            if (updatedAt != null) {
                user.setUpdatedAt(updatedAt.toInstant());
            }

            return user;
        }
    }
}
