package com.finlab.accounts.repository;

import com.finlab.accounts.dto.FraudCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Repository for transaction-related database operations using JdbcTemplate.
 */
@Repository
public class TransactionRepository {

    private static final Logger log = LoggerFactory.getLogger(TransactionRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Long saveTransaction(
        String iban,
        BigDecimal amount,
        Long vendorId,
        String invoiceNumber,
        int fraudScore,
        FraudCheckResponse.FraudDecision decision,
        List<String> riskFactors
    ) {
        String sql = """
            INSERT INTO transactions (iban, amount, vendor_id, invoice_number, fraud_score, decision, risk_factors, created_at)
            VALUES (?, ?, ?, ?, ?, ?::fraud_decision, ?::jsonb, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        try {
            jdbcTemplate.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, iban);
                ps.setBigDecimal(2, amount);
                ps.setLong(3, vendorId);
                ps.setString(4, invoiceNumber);
                ps.setInt(5, fraudScore);
                ps.setString(6, decision.name());
                ps.setString(7, toJsonArray(riskFactors));
                ps.setTimestamp(8, Timestamp.from(Instant.now()));
                return ps;
            }, keyHolder);

            Number key = keyHolder.getKey();
            return key != null ? key.longValue() : null;
        } catch (Exception e) {
            log.error("Failed to save transaction", e);
            throw new RuntimeException("Failed to save transaction", e);
        }
    }

    public int countTransactionsByIbanSince(String iban, Instant since) {
        String sql = "SELECT COUNT(*) FROM transactions WHERE iban = ? AND created_at >= ?";
        try {
            Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                iban,
                Timestamp.from(since)
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error counting transactions by IBAN", e);
            return 0;
        }
    }

    public int countTransactionsByVendorSince(Long vendorId, Instant since) {
        String sql = "SELECT COUNT(*) FROM transactions WHERE vendor_id = ? AND created_at >= ?";
        try {
            Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                vendorId,
                Timestamp.from(since)
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Error counting transactions by vendor", e);
            return 0;
        }
    }

    private String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) json.append(",");
            json.append("\"").append(items.get(i).replace("\"", "\\\"")).append("\"");
        }
        json.append("]");
        return json.toString();
    }
}
