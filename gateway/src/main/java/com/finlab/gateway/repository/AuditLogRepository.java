package com.finlab.gateway.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Map;

/**
 * Repository for audit log operations.
 */
@Repository
public class AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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

        StringBuilder json = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(entry.getKey())).append("\":");

            Object value = entry.getValue();
            if (value == null) {
                json.append("null");
            } else if (value instanceof String) {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(escapeJson(value.toString())).append("\"");
            }

            first = false;
        }

        json.append("}");
        return json.toString();
    }

    private String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
