package com.finlab.gateway.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;
import java.util.Map;

/**
 * Repository for audit log operations.
 * Audit logs are write-only and immutable (no updates/deletes).
 */
@Repository
@Transactional
public class AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Async
    public void logAuthEvent(String userId, String action, String ipAddress, String userAgent, Map<String, Object> details) {
        String sql = """
            INSERT INTO audit_log (user_id, action, resource_type, ip_address, user_agent, details, timestamp)
            VALUES (?, ?, 'AUTH', ?::inet, ?, ?::jsonb, CURRENT_TIMESTAMP)
            """;

        int[] types = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR};

        String detailsJson = details != null ? convertMapToJson(details) : "{}";

        jdbcTemplate.update(sql,
            new Object[]{userId, action, ipAddress, userAgent, detailsJson},
            types);

        log.debug("Logged auth event - User: {}, Action: {}, IP: {}", userId, action, ipAddress);
    }

    @Async
    public void logFailedAuthEvent(String action, String ipAddress, String userAgent, Map<String, Object> details) {
        String sql = """
            INSERT INTO audit_log (action, resource_type, ip_address, user_agent, details, timestamp)
            VALUES (?, 'AUTH', ?::inet, ?, ?::jsonb, CURRENT_TIMESTAMP)
            """;

        int[] types = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR};

        String detailsJson = details != null ? convertMapToJson(details) : "{}";

        jdbcTemplate.update(sql,
            new Object[]{action, ipAddress, userAgent, detailsJson},
            types);

        log.debug("Logged failed auth event - Action: {}, IP: {}", action, ipAddress);
    }

    @Async
    public void logInvoiceValidation(String userId, String resourceId, String ipAddress, String userAgent, Map<String, Object> details) {
        String sql = """
            INSERT INTO audit_log (user_id, action, resource_type, resource_id, ip_address, user_agent, details, timestamp)
            VALUES (?, 'VALIDATE_INVOICE', 'INVOICE', ?, ?::inet, ?, ?::jsonb, CURRENT_TIMESTAMP)
            """;

        int[] types = {Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR};

        String detailsJson = details != null ? convertMapToJson(details) : "{}";

        jdbcTemplate.update(sql,
            new Object[]{userId, resourceId, ipAddress, userAgent, detailsJson},
            types);

        log.debug("Logged invoice validation - User: {}, Invoice: {}, IP: {}", userId, resourceId, ipAddress);
    }

    private String convertMapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }

        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize audit details to JSON", e);
            return "{}";
        }
    }
}
