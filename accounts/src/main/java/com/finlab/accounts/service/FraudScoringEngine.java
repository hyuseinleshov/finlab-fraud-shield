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
import java.util.concurrent.CompletableFuture;
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
    private static final String REDIS_RISKY_IBAN_PREFIX = "fraud:risky:iban:";
    private static final Duration DUPLICATE_WINDOW = Duration.ofHours(24);
    private static final Duration RISKY_IBAN_CACHE_TTL = Duration.ofHours(4);

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
     * Rules execute in parallel for improved performance.
     *
     * @param request fraud check request containing transaction details
     * @return fraud check response with decision, score, and risk factors
     */
    public FraudCheckResponse checkFraud(FraudCheckRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("Starting fraud check for invoice: {}", request.invoiceNumber());

        CompletableFuture<RuleResult> duplicateCheck = CompletableFuture.supplyAsync(() ->
            checkDuplicateInvoice(request.invoiceNumber())
        );

        CompletableFuture<RuleResult> ibanValidation = CompletableFuture.supplyAsync(() ->
            checkIbanValid(request.iban())
        );

        CompletableFuture<RuleResult> riskyIbanCheck = CompletableFuture.supplyAsync(() ->
            checkRiskyIban(request.iban())
        );

        CompletableFuture<RuleResult> amountCheck = CompletableFuture.supplyAsync(() ->
            checkAmountManipulation(request.amount())
        );

        CompletableFuture<RuleResult> velocityCheck = CompletableFuture.supplyAsync(() ->
            checkVelocityAnomaly(request.iban(), request.vendorId())
        );

        CompletableFuture<Void> allChecks = CompletableFuture.allOf(
            duplicateCheck, ibanValidation, riskyIbanCheck, amountCheck, velocityCheck
        );

        int totalScore = 0;
        List<String> riskFactors = new ArrayList<>();

        try {
            allChecks.orTimeout(150, TimeUnit.MILLISECONDS).join();

            RuleResult dup = duplicateCheck.getNow(null);
            if (dup != null && dup.triggered()) {
                totalScore += dup.points();
                riskFactors.add(dup.message());
            }

            RuleResult iban = ibanValidation.getNow(null);
            if (iban != null && iban.triggered()) {
                totalScore += iban.points();
                riskFactors.add(iban.message());
            }

            RuleResult risky = riskyIbanCheck.getNow(null);
            if (risky != null && risky.triggered()) {
                totalScore += risky.points();
                riskFactors.add(risky.message());
            }

            RuleResult amount = amountCheck.getNow(null);
            if (amount != null && amount.triggered()) {
                totalScore += amount.points();
                riskFactors.add(amount.message());
            }

            RuleResult velocity = velocityCheck.getNow(null);
            if (velocity != null && velocity.triggered()) {
                totalScore += velocity.points();
                riskFactors.add(velocity.message());
            }

        } catch (Exception e) {
            log.error("Error during parallel fraud check execution", e);
        }

        long processingTime = System.currentTimeMillis() - startTime;
        log.info("Fraud check rules executed in {}ms", processingTime);

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

        log.info("Fraud check completed for invoice {}: decision={}, score={}, totalTime={}ms",
            request.invoiceNumber(), decision, totalScore, processingTime);

        return new FraudCheckResponse(decision, totalScore, riskFactors);
    }

    private RuleResult checkDuplicateInvoice(String invoiceNumber) {
        try {
            if (isDuplicateInvoice(invoiceNumber)) {
                log.warn("Duplicate invoice detected: {}", invoiceNumber);
                return new RuleResult(true, POINTS_DUPLICATE_INVOICE, "Duplicate invoice detected within 24 hours");
            }
            return RuleResult.noMatch();
        } catch (Exception e) {
            log.error("Error checking duplicate invoice", e);
            return RuleResult.noMatch();
        }
    }

    private RuleResult checkIbanValid(String iban) {
        try {
            IBANValidator.ValidationResult result = ibanValidator.validate(iban);
            if (!result.isValid()) {
                log.warn("Invalid IBAN detected: {}", result.errorMessage());
                return new RuleResult(true, POINTS_INVALID_IBAN, "Invalid IBAN: " + result.errorMessage());
            }
            return RuleResult.noMatch();
        } catch (Exception e) {
            log.error("Error validating IBAN", e);
            return RuleResult.noMatch();
        }
    }

    private RuleResult checkRiskyIban(String iban) {
        try {
            if (isRiskyIban(iban)) {
                log.warn("Risky IBAN detected");
                return new RuleResult(true, POINTS_RISKY_IBAN, "IBAN flagged as high-risk in database");
            }
            return RuleResult.noMatch();
        } catch (Exception e) {
            log.error("Error checking risky IBAN", e);
            return RuleResult.noMatch();
        }
    }

    private RuleResult checkAmountManipulation(BigDecimal amount) {
        try {
            if (isAmountManipulation(amount)) {
                log.warn("Amount manipulation detected: {}", amount);
                return new RuleResult(true, POINTS_AMOUNT_MANIPULATION, "Amount suspiciously close to common threshold");
            }
            return RuleResult.noMatch();
        } catch (Exception e) {
            log.error("Error checking amount manipulation", e);
            return RuleResult.noMatch();
        }
    }

    private RuleResult checkVelocityAnomaly(String iban, Long vendorId) {
        try {
            if (isVelocityAnomaly(iban, vendorId)) {
                log.warn("Velocity anomaly detected");
                return new RuleResult(true, POINTS_VELOCITY_ANOMALY, "Unusual transaction velocity detected");
            }
            return RuleResult.noMatch();
        } catch (Exception e) {
            log.error("Error checking velocity anomaly", e);
            return RuleResult.noMatch();
        }
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
            String cacheKey = REDIS_RISKY_IBAN_PREFIX + iban;
            String cached = redisTemplate.opsForValue().get(cacheKey);

            if (cached != null) {
                log.debug("Risky IBAN cache hit for IBAN");
                return Boolean.parseBoolean(cached);
            }

            log.debug("Risky IBAN cache miss, querying database");
            boolean isRisky = ibanRepository.isRiskyIban(iban);

            redisTemplate.opsForValue().set(cacheKey, String.valueOf(isRisky), RISKY_IBAN_CACHE_TTL);

            return isRisky;
        } catch (Exception e) {
            log.error("Error during risky IBAN check", e);
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

    private record RuleResult(boolean triggered, int points, String message) {
        static RuleResult noMatch() {
            return new RuleResult(false, 0, null);
        }
    }
}
