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
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("1500.00"),
            1L,
            "INV-001"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // Act
        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        // Assert
        assertEquals(FraudCheckResponse.FraudDecision.ALLOW, response.decision());
        assertEquals(0, response.fraudScore());
        assertTrue(response.riskFactors().isEmpty());
    }

    @Test
    void checkFraud_WithDuplicateInvoice_ShouldAddFiftyPoints() {
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("1500.00"),
            1L,
            "INV-DUPLICATE"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // Act
        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        // Assert
        assertEquals(FraudCheckResponse.FraudDecision.REVIEW, response.decision());
        assertEquals(50, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("Duplicate invoice")));
    }

    @Test
    void checkFraud_WithInvalidIban_ShouldAddFiftyPoints() {
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG99INVALID00000000000",
            new BigDecimal("1500.00"),
            1L,
            "INV-002"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString()))
            .thenReturn(IBANValidator.ValidationResult.invalid("Invalid IBAN checksum"));
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // Act
        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        // Assert
        assertEquals(FraudCheckResponse.FraudDecision.REVIEW, response.decision());
        assertEquals(50, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("Invalid IBAN")));
    }

    @Test
    void checkFraud_WithRiskyIban_ShouldAddFortyPoints() {
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("1500.00"),
            1L,
            "INV-003"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(true);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // Act
        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        // Assert
        assertEquals(FraudCheckResponse.FraudDecision.REVIEW, response.decision());
        assertEquals(40, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("high-risk")));
    }

    @Test
    void checkFraud_WithAmountManipulation_ShouldAddThirtyPoints() {
        // Arrange - amount just below 5000 threshold
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("4990.00"),
            1L,
            "INV-004"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // Act
        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        // Assert
        assertEquals(FraudCheckResponse.FraudDecision.ALLOW, response.decision());
        assertEquals(30, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("threshold")));
    }

    @Test
    void checkFraud_WithVelocityAnomaly_ShouldAddFifteenPoints() {
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("1500.00"),
            1L,
            "INV-005"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(6L);

        // Act
        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        // Assert
        assertEquals(FraudCheckResponse.FraudDecision.ALLOW, response.decision());
        assertEquals(15, response.fraudScore());
        assertTrue(response.riskFactors().stream()
            .anyMatch(f -> f.contains("velocity")));
    }

    @Test
    void checkFraud_WithMultipleRiskFactors_ShouldBlockTransaction() {
        // Arrange - duplicate + invalid IBAN = 100 points
        FraudCheckRequest request = new FraudCheckRequest(
            "BG99INVALID00000000000",
            new BigDecimal("1500.00"),
            1L,
            "INV-BLOCKED"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(false);
        when(ibanValidator.validate(anyString()))
            .thenReturn(IBANValidator.ValidationResult.invalid("Invalid checksum"));
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // Act
        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        // Assert
        assertEquals(FraudCheckResponse.FraudDecision.BLOCK, response.decision());
        assertEquals(100, response.fraudScore());
        assertEquals(2, response.riskFactors().size());
    }

    @Test
    void checkFraud_WithScoreAtBoundary_ShouldReturnCorrectDecision() {
        // Arrange - exactly 30 points (ALLOW boundary)
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("4990.00"),
            1L,
            "INV-006"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);

        // Act
        FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

        // Assert
        assertEquals(FraudCheckResponse.FraudDecision.ALLOW, response.decision());
        assertEquals(30, response.fraudScore());
    }

    @Test
    void checkFraud_ShouldRecordTransactionInDatabase() {
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("1500.00"),
            1L,
            "INV-007"
        );

        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);
        when(ibanValidator.validate(anyString())).thenReturn(IBANValidator.ValidationResult.valid());
        when(ibanRepository.isRiskyIban(anyString())).thenReturn(false);
        when(zSetOperations.count(anyString(), anyDouble(), anyDouble())).thenReturn(0L);
        when(transactionRepository.saveTransaction(any(), any(), any(), any(), anyInt(), any(), any()))
            .thenReturn(1L);

        // Act
        fraudScoringEngine.checkFraud(request);

        // Assert
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
