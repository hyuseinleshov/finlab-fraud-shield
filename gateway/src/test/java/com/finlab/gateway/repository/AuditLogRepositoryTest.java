package com.finlab.gateway.repository;

import com.finlab.gateway.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogRepositoryTest extends BaseIntegrationTest {

    // Test data constants
    private static final String TEST_USER_ID = "testuser";
    private static final String TEST_USERNAME_NONEXISTENT = "nonexistent";
    private static final String IP_ADDRESS_LOCALHOST = "127.0.0.1";
    private static final String IP_ADDRESS_PRIVATE = "192.168.1.100";
    private static final String IP_ADDRESS_PRIVATE_10 = "10.0.0.1";
    private static final String USER_AGENT_TEST = "TestAgent/1.0";

    // Auth actions
    private static final String ACTION_LOGIN = "LOGIN";
    private static final String ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    private static final String ACTION_LOGOUT = "LOGOUT";

    // Detail keys
    private static final String DETAIL_METHOD = "method";
    private static final String DETAIL_SUCCESS = "success";
    private static final String DETAIL_USERNAME = "username";
    private static final String DETAIL_REASON = "reason";

    // Detail values
    private static final String METHOD_PASSWORD = "password";
    private static final String REASON_INVALID_CREDENTIALS = "invalid_credentials";

    // SQL queries
    private static final String SQL_COUNT_BY_USER_AND_ACTION = "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = ?";
    private static final String SQL_COUNT_BY_ACTION_NULL_USER = "SELECT COUNT(*) FROM audit_log WHERE action = ? AND user_id IS NULL";

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void logAuthEvent_ShouldInsertAuditLog() {
        Map<String, Object> details = new HashMap<>();
        details.put(DETAIL_METHOD, METHOD_PASSWORD);
        details.put(DETAIL_SUCCESS, true);

        auditLogRepository.logAuthEvent(TEST_USER_ID, ACTION_LOGIN, IP_ADDRESS_LOCALHOST, USER_AGENT_TEST, details);

        Integer count = jdbcTemplate.queryForObject(SQL_COUNT_BY_USER_AND_ACTION, Integer.class, TEST_USER_ID, ACTION_LOGIN);
        assertNotNull(count);
        assertTrue(count > 0, "Audit log should be inserted");
    }

    @Test
    void logFailedAuthEvent_WithoutUserId_ShouldInsertAuditLog() {
        Map<String, Object> details = new HashMap<>();
        details.put(DETAIL_USERNAME, TEST_USERNAME_NONEXISTENT);
        details.put(DETAIL_REASON, REASON_INVALID_CREDENTIALS);

        auditLogRepository.logFailedAuthEvent(ACTION_LOGIN_FAILED, IP_ADDRESS_PRIVATE, USER_AGENT_TEST, details);

        Integer count = jdbcTemplate.queryForObject(SQL_COUNT_BY_ACTION_NULL_USER, Integer.class, ACTION_LOGIN_FAILED);
        assertNotNull(count);
        assertTrue(count > 0, "Failed auth event should be logged without user_id");
    }

    @Test
    void logAuthEvent_WithNullDetails_ShouldInsertEmptyJson() {
        auditLogRepository.logAuthEvent(TEST_USER_ID, ACTION_LOGOUT, IP_ADDRESS_PRIVATE_10, USER_AGENT_TEST, null);

        Integer count = jdbcTemplate.queryForObject(SQL_COUNT_BY_USER_AND_ACTION, Integer.class, TEST_USER_ID, ACTION_LOGOUT);
        assertNotNull(count);
        assertTrue(count > 0, "Audit log should handle null details");
    }
}
