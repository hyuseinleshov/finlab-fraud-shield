package com.finlab.accounts.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Repository for IBAN-related database operations using JdbcTemplate.
 */
@Repository
public class IBANRepository {

    private static final Logger log = LoggerFactory.getLogger(IBANRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public IBANRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isRiskyIban(String iban) {
        try {
            String sql = "SELECT is_risky FROM ibans WHERE iban = ? LIMIT 1";

            int[] types = {java.sql.Types.VARCHAR};

            Boolean isRisky = jdbcTemplate.queryForObject(
                sql,
                new Object[]{iban},
                types,
                Boolean.class
            );
            return Boolean.TRUE.equals(isRisky);
        } catch (Exception e) {
            log.debug("IBAN {} not found in database or error occurred", maskIban(iban));
            return false;
        }
    }

    private String maskIban(String iban) {
        if (iban == null || iban.length() <= 8) {
            return "****";
        }
        return iban.substring(0, 4) + "****" + iban.substring(iban.length() - 4);
    }
}
