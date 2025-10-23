package com.finlab.accounts.service;

import com.finlab.accounts.dto.FraudCheckRequest;
import com.finlab.accounts.dto.FraudCheckResponse;
import com.finlab.accounts.repository.IBANRepository;
import com.finlab.accounts.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudScoringEngineTest {

    // Fraud scoring constants (must match FraudScoringEngine implementation)
    private static final int POINTS_DUPLICATE_INVOICE = 50;
    private static final int POINTS_INVALID_IBAN = 50;
    private static final int POINTS_RISKY_IBAN = 40;
    private static final int POINTS_AMOUNT_MANIPULATION = 30;
    private static final int POINTS_VELOCITY_ANOMALY = 15;

    // Decision thresholds
    private static final int ALLOW_THRESHOLD = 30;

    // Test data constants
    private static final String VALID_BULGARIAN_IBAN = "BG80BNBG96611020345678";
    private static final String INVALID_IBAN = "BG99INVALID00000000000";
    private static final BigDecimal NORMAL_AMOUNT = new BigDecimal("1500.00");
    private static final BigDecimal THRESHOLD_MANIPULATION_AMOUNT = new BigDecimal("4990.00");
    private static final Long TEST_VENDOR_ID = 1L;
    private static final String TEST_INVOICE_PREFIX = "INV-";

    // Velocity thresholds
    private static final long VELOCITY_NO_ANOMALY = 0L;
    private static final long VELOCITY_THRESHOLD_EXCEEDED = 6L;

    @Mock
    private IBANValidator ibanValidator;

    @Mock
    private IBANRepository ibanRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private FraudScoringEngine fraudScoringEngine;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        fraudScoringEngine = new FraudScoringEngine(
            ibanValidator,
            ibanRepository,
            transactionRepository,
            redisTemplate
        );
    }

    @Test
    void checkFraud_WithValidTransaction_ShouldReturnAllow() {
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "001"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_NO_ANOMALY);

        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        assertEquals(FraudCheckResponse.FraudDecision.ALLOW, response.decision());
        assertEquals(0, response.fraudScore());
        assertTrue(response.riskFactors().isEmpty());
    }

    @Test
    void checkFraud_WithDuplicateInvoice_ShouldAddFiftyPoints() {
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "DUPLICATE"
        );

        // Simulate duplicate invoice (setIfAbsent returns false)
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_NO_ANOMALY);

        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        assertEquals(FraudCheckResponse.FraudDecision.REVIEW, response.decision());
        assertEquals(POINTS_DUPLICATE_INVOICE, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("Duplicate invoice")));
    }

    @Test
    void checkFraud_WithInvalidIban_ShouldAddFiftyPoints() {
        FraudCheckRequest request = new FraudCheckRequest(
            INVALID_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "002"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString()))
            .thenReturn(IBANValidator.ValidationResult.invalid("Invalid IBAN checksum"));
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_NO_ANOMALY);

        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        assertEquals(FraudCheckResponse.FraudDecision.REVIEW, response.decision());
        assertEquals(POINTS_INVALID_IBAN, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("Invalid IBAN")));
    }

    @Test
    void checkFraud_WithRiskyIban_ShouldAddFortyPoints() {
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "003"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(true);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_NO_ANOMALY);

        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        assertEquals(FraudCheckResponse.FraudDecision.REVIEW, response.decision());
        assertEquals(POINTS_RISKY_IBAN, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("high-risk")));
    }

    @Test
    void checkFraud_WithAmountManipulation_ShouldAddThirtyPoints() {
        // Amount just below 5000 threshold (4990 is within 50 of 4999 threshold)
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            THRESHOLD_MANIPULATION_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "004"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_NO_ANOMALY);

        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        assertEquals(FraudCheckResponse.FraudDecision.ALLOW, response.decision());
        assertEquals(POINTS_AMOUNT_MANIPULATION, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("threshold")));
    }

    @Test
    void checkFraud_WithVelocityAnomaly_ShouldAddFifteenPoints() {
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "005"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        // 6 transactions exceeds the threshold of 5
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_THRESHOLD_EXCEEDED);

        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        assertEquals(FraudCheckResponse.FraudDecision.ALLOW, response.decision());
        assertEquals(POINTS_VELOCITY_ANOMALY, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("velocity")));
    }

    @Test
    void checkFraud_WithMultipleRiskFactors_ShouldBlockTransaction() {
        // Duplicate invoice (50) + invalid IBAN (50) = 100 points (BLOCK)
        int expectedScore = POINTS_DUPLICATE_INVOICE + POINTS_INVALID_IBAN;

        FraudCheckRequest request = new FraudCheckRequest(
            INVALID_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "BLOCKED"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);
        when(ibanValidator.validate(anyString()))
            .thenReturn(IBANValidator.ValidationResult.invalid("Invalid checksum"));
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_NO_ANOMALY);

        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        assertEquals(FraudCheckResponse.FraudDecision.BLOCK, response.decision());
        assertEquals(expectedScore, response.fraudScore());
        assertEquals(2, response.riskFactors().size());
    }

    @Test
    void checkFraud_WithScoreAtBoundary_ShouldReturnCorrectDecision() {
        // Exactly at ALLOW threshold (30 points from amount manipulation)
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            THRESHOLD_MANIPULATION_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "006"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_NO_ANOMALY);

        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        assertEquals(FraudCheckResponse.FraudDecision.ALLOW, response.decision());
        assertEquals(ALLOW_THRESHOLD, response.fraudScore());
    }

    @Test
    void checkFraud_ShouldRecordTransactionInDatabase() {
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            TEST_INVOICE_PREFIX + "007"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(VELOCITY_NO_ANOMALY);
        when(transactionRepository.saveTransaction(any(), any(), any(), any(), anyInt(), any(), any()))
            .thenReturn(1L);

        fraudScoringEngine.checkFraud(request);

        verify(transactionRepository, times(1)).saveTransaction(
            eq(request.iban()),
            eq(request.amount()),
            eq(request.vendorId()),
            eq(request.invoiceNumber()),
            anyInt(),
            any(FraudCheckResponse.FraudDecision.class),
            anyList()
        );
    }
}
