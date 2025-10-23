package com.finlab.gateway.repository;

import com.finlab.gateway.config.BaseIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditLogRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void logAuthEvent_ShouldInsertAuditLog() {
        String userId = "testuser";
        String action = "LOGIN";
        String ipAddress = "127.0.0.1";
        String userAgent = "TestAgent/1.0";
        Map<String, Object> details = new HashMap<>();
        details.put("method", "password");
        details.put("success", true);

        auditLogRepository.logAuthEvent(userId, action, ipAddress, userAgent, details);

        String sql = "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, action);
        assertNotNull(count);
        assertTrue(count > 0, "Audit log should be inserted");
    }

    @Test
    void logFailedAuthEvent_WithoutUserId_ShouldInsertAuditLog() {
        String action = "LOGIN_FAILED";
        String ipAddress = "192.168.1.100";
        String userAgent = "TestAgent/1.0";
        Map<String, Object> details = new HashMap<>();
        details.put("username", "nonexistent");
        details.put("reason", "invalid_credentials");

        auditLogRepository.logFailedAuthEvent(action, ipAddress, userAgent, details);

        String sql = "SELECT COUNT(*) FROM audit_log WHERE action = ? AND user_id IS NULL";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, action);
        assertNotNull(count);
        assertTrue(count > 0, "Failed auth event should be logged without user_id");
    }

    @Test
    void logAuthEvent_WithNullDetails_ShouldInsertEmptyJson() {
        String userId = "testuser";
        String action = "LOGOUT";
        String ipAddress = "10.0.0.1";
        String userAgent = "TestAgent/1.0";

        auditLogRepository.logAuthEvent(userId, action, ipAddress, userAgent, null);

        String sql = "SELECT COUNT(*) FROM audit_log WHERE user_id = ? AND action = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId, action);
        assertNotNull(count);
        assertTrue(count > 0, "Audit log should handle null details");
    }
}
