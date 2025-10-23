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
 * Fraud scoring engine implementing three-tier decision framework:
 * ALLOW (0-30), REVIEW (31-70), BLOCK (71-100).
 */
@Service
public class FraudScoringEngine {

    private static final Logger log = LoggerFactory.getLogger(FraudScoringEngine.class);

    private static final int ALLOW_THRESHOLD = 30;
    private static final int REVIEW_THRESHOLD = 70;

    private static final int POINTS_DUPLICATE_INVOICE = 50;
    private static final int POINTS_INVALID_IBAN = 50;
    private static final int POINTS_RISKY_IBAN = 40;
    private static final int POINTS_AMOUNT_MANIPULATION = 30;
    private static final int POINTS_VELOCITY_ANOMALY = 15;

    private static final BigDecimal[] SUSPICIOUS_THRESHOLDS = {
        new BigDecimal("999"),
        new BigDecimal("1999"),
        new BigDecimal("4999"),
        new BigDecimal("9999"),
        new BigDecimal("14999"),
        new BigDecimal("19999"),
        new BigDecimal("49999")
    };
    private static final BigDecimal THRESHOLD_MARGIN = new BigDecimal("50");

    private static final Duration VELOCITY_WINDOW = Duration.ofMinutes(15);
    private static final int VELOCITY_THRESHOLD_IBAN = 5;
    private static final int VELOCITY_THRESHOLD_VENDOR = 10;

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
     * Performs comprehensive fraud check on a transaction.
     *
     * @param request fraud check request containing transaction details
     * @return fraud check response with decision, score, and risk factors
     */
    public FraudCheckResponse checkFraud(FraudCheckRequest request) {
        log.info("Starting fraud check for invoice: {}", request.invoiceNumber());

        int totalScore = 0;
        List<String> riskFactors = new ArrayList<>();

        if (isDuplicateInvoice(request.invoiceNumber())) {
            totalScore += POINTS_DUPLICATE_INVOICE;
            riskFactors.add("Duplicate invoice detected within 24 hours");
            log.warn("Duplicate invoice detected: {}", request.invoiceNumber());
        }

        IBANValidator.ValidationResult ibanValidation = ibanValidator.validate(request.iban());
        if (!ibanValidation.isValid()) {
            totalScore += POINTS_INVALID_IBAN;
            riskFactors.add("Invalid IBAN: " + ibanValidation.errorMessage());
            log.warn("Invalid IBAN detected for invoice {}: {}", request.invoiceNumber(), ibanValidation.errorMessage());
        }

        if (isRiskyIban(request.iban())) {
            totalScore += POINTS_RISKY_IBAN;
            riskFactors.add("IBAN flagged as high-risk in database");
            log.warn("Risky IBAN detected for invoice {}", request.invoiceNumber());
        }

        if (isAmountManipulation(request.amount())) {
            totalScore += POINTS_AMOUNT_MANIPULATION;
            riskFactors.add("Amount suspiciously close to common threshold");
            log.warn("Amount manipulation detected for invoice {}: {}", request.invoiceNumber(), request.amount());
        }

        if (isVelocityAnomaly(request.iban(), request.vendorId())) {
            totalScore += POINTS_VELOCITY_ANOMALY;
            riskFactors.add("Unusual transaction velocity detected");
            log.warn("Velocity anomaly detected for invoice {}", request.invoiceNumber());
        }

        FraudCheckResponse.FraudDecision decision = determineDecision(totalScore);

        recordTransaction(request.iban(), request.vendorId(), request.invoiceNumber());

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

    private boolean isRiskyIban(String iban) {
        try {
            return ibanRepository.isRiskyIban(iban);
        } catch (Exception e) {
            log.error("Database error during risky IBAN check", e);
            return false;
        }
    }

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

    private boolean isVelocityAnomaly(String iban, Long vendorId) {
        try {
            int ibanCount = getTransactionCountInWindow(
                REDIS_VELOCITY_IBAN_PREFIX + iban,
                iban,
                true
            );
            if (ibanCount >= VELOCITY_THRESHOLD_IBAN) {
                log.debug("IBAN velocity threshold exceeded: {} transactions", ibanCount);
                return true;
            }

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
            Instant since = Instant.now().minus(VELOCITY_WINDOW);
            if (isIban) {
                return transactionRepository.countTransactionsByIbanSince(identifier, since);
            } else {
                return transactionRepository.countTransactionsByVendorSince(Long.parseLong(identifier), since);
            }
        }
    }

    private void recordTransaction(String iban, Long vendorId, String invoiceNumber) {
        try {
            double timestamp = System.currentTimeMillis();

            String ibanKey = REDIS_VELOCITY_IBAN_PREFIX + iban;
            redisTemplate.opsForZSet().add(ibanKey, invoiceNumber, timestamp);
            redisTemplate.expire(ibanKey, VELOCITY_WINDOW.toMillis(), TimeUnit.MILLISECONDS);

            String vendorKey = REDIS_VELOCITY_VENDOR_PREFIX + vendorId;
            redisTemplate.opsForZSet().add(vendorKey, invoiceNumber, timestamp);
            redisTemplate.expire(vendorKey, VELOCITY_WINDOW.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.error("Failed to record transaction velocity", e);
        }
    }

    private double getCurrentWindowStart() {
        return System.currentTimeMillis() - VELOCITY_WINDOW.toMillis();
    }

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
