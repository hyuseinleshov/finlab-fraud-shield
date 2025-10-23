package com.finlab.accounts.service;

import com.finlab.accounts.dto.FraudCheckRequest;
import com.finlab.accounts.dto.FraudCheckResponse;
import com.finlab.accounts.repository.IBANRepository;
import com.finlab.accounts.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Fraud scoring engine implementing three-tier decision framework.
 * <p>
 * Score ranges:
 * - 0-30: ALLOW
 * - 31-70: REVIEW
 * - 71-100: BLOCK
 */
@Service
public class FraudScoringEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringEngine.class);

    // Score thresholds
    private static final int ALLOW_THRESHOLD = 30;
    private static final int REVIEW_THRESHOLD = 70;

    // Fraud rule points
    private static final int POINTS_DUPLICATE_INVOICE = 50;
    private static final int POINTS_INVALID_IBAN = 50;
    private static final int POINTS_RISKY_IBAN = 40;
    private static final int POINTS_AMOUNT_MANIPULATION = 30;
    private static final int POINTS_VELOCITY_ANOMALY = 15;

    // Amount manipulation thresholds (just below common limits)
    private static final BigDecimal[] SUSPICIOUS_THRESHOLDS = {
        new BigDecimal("999"),      // Just below 1000
        new BigDecimal("1999"),     // Just below 2000
        new BigDecimal("4999"),     // Just below 5000
        new BigDecimal("9999"),     // Just below 10000
        new BigDecimal("14999"),    // Just below 15000
        new BigDecimal("19999"),    // Just below 20000
        new BigDecimal("49999"),    // Just below 50000
    };
    private static final BigDecimal THRESHOLD_MARGIN = new BigDecimal("50");

    // Velocity detection windows
    private static final Duration VELOCITY_WINDOW = Duration.ofMinutes(15);
    private static final int VELOCITY_THRESHOLD_IBAN = 5;  // 5+ transactions from same IBAN in 15min
    private static final int VELOCITY_THRESHOLD_VENDOR = 10; // 10+ transactions to same vendor in 15min

    // Redis key prefixes
    private static final String REDIS_DUPLICATE_PREFIX = "fraud:duplicate:";
    private static final String REDIS_VELOCITY_IBAN_PREFIX = "fraud:velocity:iban:";
    private static final String REDIS_VELOCITY_VENDOR_PREFIX = "fraud:velocity:vendor:";
    private static final Duration DUPLICATE_WINDOW = Duration.ofHours(24);

    private final IBANValidator ibanValidator;
    private final IBANRepository ibanRepository;
    private final TransactionRepository transactionRepository;
    private final StringRedisTemplate redisTemplate;

    public FraudScoringEngine(
        IBANValidator ibanValidator,
        IBANRepository ibanRepository,
        TransactionRepository transactionRepository,
        StringRedisTemplate redisTemplate
    ) {
        this.ibanValidator = ibanValidator;
        this.ibanRepository = ibanRepository;
        this.transactionRepository = transactionRepository;
        this.redisTemplate = redisTemplate;
    }

    /**
     * Perform comprehensive fraud check on a transaction.
     */
    public FraudCheckResponse checkFraud(FraudCheckRequest request) {
        log.info("Starting fraud check for invoice: {}", request.invoiceNumber());

        int totalScore = 0;
        List<String> riskFactors = new ArrayList<>();

        // Rule 1: Check for duplicate invoice (Redis Set, 24h window) - +50 points
        if (isDuplicateInvoice(request.invoiceNumber())) {
            totalScore += POINTS_DUPLICATE_INVOICE;
            riskFactors.add("Duplicate invoice detected within 24 hours");
            log.warn("Duplicate invoice detected: {}", request.invoiceNumber());
        }

        // Rule 2: Validate IBAN (MOD 97 check) - +50 points if invalid
        IBANValidator.ValidationResult ibanValidation = ibanValidator.validate(request.iban());
        if (!ibanValidation.isValid()) {
            totalScore += POINTS_INVALID_IBAN;
            riskFactors.add("Invalid IBAN: " + ibanValidation.errorMessage());
            log.warn("Invalid IBAN detected for invoice {}: {}", request.invoiceNumber(), ibanValidation.errorMessage());
        }

        // Rule 3: Check if IBAN is marked as risky in database - +40 points
        if (isRiskyIban(request.iban())) {
            totalScore += POINTS_RISKY_IBAN;
            riskFactors.add("IBAN flagged as high-risk in database");
            log.warn("Risky IBAN detected for invoice {}", request.invoiceNumber());
        }

        // Rule 4: Detect amount manipulation (just below thresholds) - +30 points
        if (isAmountManipulation(request.amount())) {
            totalScore += POINTS_AMOUNT_MANIPULATION;
            riskFactors.add("Amount suspiciously close to common threshold");
            log.warn("Amount manipulation detected for invoice {}: {}", request.invoiceNumber(), request.amount());
        }

        // Rule 5: Velocity anomaly detection (Redis ZSet) - +15 points
        if (isVelocityAnomaly(request.iban(), request.vendorId())) {
            totalScore += POINTS_VELOCITY_ANOMALY;
            riskFactors.add("Unusual transaction velocity detected");
            log.warn("Velocity anomaly detected for invoice {}", request.invoiceNumber());
        }

        // Determine decision based on score
        FraudCheckResponse.FraudDecision decision = determineDecision(totalScore);

        // Record velocity tracking
        recordTransaction(request.iban(), request.vendorId(), request.invoiceNumber());

        // Persist transaction to database
        try {
            transactionRepository.saveTransaction(
                request.iban(),
                request.amount(),
                request.vendorId(),
                request.invoiceNumber(),
                totalScore,
                decision,
                riskFactors
            );
        } catch (Exception e) {
            log.error("Failed to persist transaction, but fraud check completed", e);
        }

        log.info("Fraud check completed for invoice {}: decision={}, score={}",
            request.invoiceNumber(), decision, totalScore);

        return new FraudCheckResponse(decision, totalScore, riskFactors);
    }

    /**
     * Check for duplicate invoice using Redis Set with 24-hour TTL.
     */
    private boolean isDuplicateInvoice(String invoiceNumber) {
        try {
            String key = REDIS_DUPLICATE_PREFIX + invoiceNumber;
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(
                key,
                "1",
                DUPLICATE_WINDOW
            );
            return !Boolean.TRUE.equals(isNew);
        } catch (Exception e) {
            log.error("Redis error during duplicate check, allowing transaction", e);
            return false;
        }
    }

    /**
     * Check if IBAN is marked as risky in the database.
     */
    private boolean isRiskyIban(String iban) {
        try {
            return ibanRepository.isRiskyIban(iban);
        } catch (Exception e) {
            log.error("Database error during risky IBAN check", e);
            return false;
        }
    }

    /**
     * Detect if amount is suspiciously close to common thresholds.
     */
    private boolean isAmountManipulation(BigDecimal amount) {
        for (BigDecimal threshold : SUSPICIOUS_THRESHOLDS) {
            BigDecimal lowerBound = threshold.subtract(THRESHOLD_MARGIN);
            BigDecimal upperBound = threshold.add(BigDecimal.ONE);

            if (amount.compareTo(lowerBound) >= 0 && amount.compareTo(upperBound) <= 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Detect velocity anomalies using Redis and database fallback.
     */
    private boolean isVelocityAnomaly(String iban, Long vendorId) {
        try {
            // Check IBAN velocity
            int ibanCount = getTransactionCountInWindow(
                REDIS_VELOCITY_IBAN_PREFIX + iban,
                iban,
                true
            );
            if (ibanCount >= VELOCITY_THRESHOLD_IBAN) {
                log.debug("IBAN velocity threshold exceeded: {} transactions", ibanCount);
                return true;
            }

            // Check vendor velocity
            int vendorCount = getTransactionCountInWindow(
                REDIS_VELOCITY_VENDOR_PREFIX + vendorId,
                vendorId.toString(),
                false
            );
            if (vendorCount >= VELOCITY_THRESHOLD_VENDOR) {
                log.debug("Vendor velocity threshold exceeded: {} transactions", vendorCount);
                return true;
            }

            return false;
        } catch (Exception e) {
            log.error("Error during velocity check", e);
            return false;
        }
    }

    /**
     * Get transaction count within velocity window, with database fallback.
     */
    private int getTransactionCountInWindow(String redisKey, String identifier, boolean isIban) {
        try {
            Long count = redisTemplate.opsForZSet().count(
                redisKey,
                getCurrentWindowStart(),
                Double.MAX_VALUE
            );
            return count != null ? count.intValue() : 0;
        } catch (Exception e) {
            log.warn("Redis velocity check failed, falling back to database", e);
            // Fallback to database
            Instant since = Instant.now().minus(VELOCITY_WINDOW);
            if (isIban) {
                return transactionRepository.countTransactionsByIbanSince(identifier, since);
            } else {
                return transactionRepository.countTransactionsByVendorSince(Long.parseLong(identifier), since);
            }
        }
    }

    /**
     * Record transaction for velocity tracking using Redis ZSet.
     */
    private void recordTransaction(String iban, Long vendorId, String invoiceNumber) {
        try {
            double timestamp = System.currentTimeMillis();

            // Record IBAN velocity
            String ibanKey = REDIS_VELOCITY_IBAN_PREFIX + iban;
            redisTemplate.opsForZSet().add(ibanKey, invoiceNumber, timestamp);
            redisTemplate.expire(ibanKey, VELOCITY_WINDOW.toMillis(), TimeUnit.MILLISECONDS);

            // Record vendor velocity
            String vendorKey = REDIS_VELOCITY_VENDOR_PREFIX + vendorId;
            redisTemplate.opsForZSet().add(vendorKey, invoiceNumber, timestamp);
            redisTemplate.expire(vendorKey, VELOCITY_WINDOW.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to record transaction velocity", e);
        }
    }

    /**
     * Get start of current velocity window as timestamp.
     */
    private double getCurrentWindowStart() {
        return System.currentTimeMillis() - VELOCITY_WINDOW.toMillis();
    }

    /**
     * Determine fraud decision based on total score.
     */
    private FraudCheckResponse.FraudDecision determineDecision(int score) {
        if (score <= ALLOW_THRESHOLD) {
            return FraudCheckResponse.FraudDecision.ALLOW;
        } else if (score <= REVIEW_THRESHOLD) {
            return FraudCheckResponse.FraudDecision.REVIEW;
        } else {
            return FraudCheckResponse.FraudDecision.BLOCK;
        }
    }
}
