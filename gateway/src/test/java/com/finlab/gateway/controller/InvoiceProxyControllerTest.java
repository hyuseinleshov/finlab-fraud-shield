package com.finlab.gateway.controller;

import com.finlab.gateway.dto.FraudCheckRequest;
import com.finlab.gateway.dto.FraudCheckResponse;
import com.finlab.gateway.repository.AuditLogRepository;
import com.finlab.gateway.service.AccountsServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class InvoiceProxyControllerTest {

    // Test data constants
    private static final String VALID_BULGARIAN_IBAN = "BG80BNBG96611020345678";
    private static final BigDecimal NORMAL_AMOUNT = new BigDecimal("1500.00");
    private static final Long TEST_VENDOR_ID = 1L;
    private static final String INVOICE_NORMAL = "INV-2025-001";

    // Test user/auth constants
    private static final String TEST_USERNAME = "testuser";

    // Network constants
    private static final String IP_REMOTE_ADDR = "192.168.1.100";
    private static final String IP_X_FORWARDED_FOR_SINGLE = "203.0.113.195";
    private static final String IP_X_FORWARDED_FOR_MULTIPLE = "203.0.113.195, 198.51.100.178";
    private static final String IP_X_REAL_IP = "203.0.113.42";
    private static final String IP_FALLBACK = "10.0.0.5";
    private static final String USER_AGENT_MOZILLA = "Mozilla/5.0";
    private static final String USER_AGENT_UNKNOWN = "unknown";

    // HTTP headers
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";

    // Fraud scores
    private static final int SCORE_LOW_RISK = 15;
    private static final int SCORE_MEDIUM_RISK = 45;
    private static final int SCORE_HIGH_RISK = 85;

    // HTTP status codes
    private static final int HTTP_OK = 200;

    // Risk factor messages
    private static final String RISK_LOW = "Low risk transaction";
    private static final String RISK_DUPLICATE_INVOICE = "Duplicate invoice detected";
    private static final String RISK_INVALID_IBAN = "Invalid IBAN";
    private static final String RISK_AMOUNT_THRESHOLD = "Amount near threshold";

    // Audit detail keys
    private static final String DETAIL_KEY_IBAN = "iban";
    private static final String DETAIL_KEY_AMOUNT = "amount";
    private static final String DETAIL_KEY_VENDOR_ID = "vendorId";

    @Mock
    private AccountsServiceClient accountsServiceClient;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest httpServletRequest;

    @InjectMocks
    private InvoiceProxyController invoiceProxyController;

    private FraudCheckRequest validRequest;
    private FraudCheckResponse mockResponse;

    @BeforeEach
    void setUp() {
        validRequest = new FraudCheckRequest(
                VALID_BULGARIAN_IBAN,
                NORMAL_AMOUNT,
                TEST_VENDOR_ID,
                INVOICE_NORMAL
        );

        mockResponse = new FraudCheckResponse(
                FraudCheckResponse.FraudDecision.ALLOW,
                SCORE_LOW_RISK,
                List.of(RISK_LOW)
        );

        when(authentication.getName()).thenReturn(TEST_USERNAME);
        when(httpServletRequest.getRemoteAddr()).thenReturn(IP_REMOTE_ADDR);
        when(httpServletRequest.getHeader(HEADER_USER_AGENT)).thenReturn(USER_AGENT_MOZILLA);
        when(httpServletRequest.getHeader(HEADER_X_FORWARDED_FOR)).thenReturn(null);
        when(httpServletRequest.getHeader(HEADER_X_REAL_IP)).thenReturn(null);
    }

    @Test
    void validateInvoice_WithValidRequest_ShouldReturnSuccessResponse() {
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<FraudCheckResponse> response = invoiceProxyController.validateInvoice(
                validRequest, authentication, httpServletRequest
        );

        assertThat(response.getStatusCodeValue()).isEqualTo(HTTP_OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(FraudCheckResponse.FraudDecision.ALLOW);
        assertThat(response.getBody().fraudScore()).isEqualTo(SCORE_LOW_RISK);

        verify(accountsServiceClient, times(1)).validateInvoice(validRequest);
        verify(auditLogRepository, times(1)).logInvoiceValidation(
                eq(TEST_USERNAME),
                eq(INVOICE_NORMAL),
                anyString(),
                anyString(),
                anyMap()
        );
    }

    @Test
    void validateInvoice_WithHighRiskInvoice_ShouldReturnBlockDecision() {
        FraudCheckResponse blockResponse = new FraudCheckResponse(
                FraudCheckResponse.FraudDecision.BLOCK,
                SCORE_HIGH_RISK,
                List.of(RISK_DUPLICATE_INVOICE, RISK_INVALID_IBAN)
        );

        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(blockResponse);

        ResponseEntity<FraudCheckResponse> response = invoiceProxyController.validateInvoice(
                validRequest, authentication, httpServletRequest
        );

        assertThat(response.getStatusCodeValue()).isEqualTo(HTTP_OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(FraudCheckResponse.FraudDecision.BLOCK);
        assertThat(response.getBody().fraudScore()).isEqualTo(SCORE_HIGH_RISK);
        assertThat(response.getBody().riskFactors()).hasSize(2);
    }

    @Test
    void validateInvoice_ShouldExtractIpFromXForwardedForHeader() {
        when(httpServletRequest.getHeader(HEADER_X_FORWARDED_FOR))
                .thenReturn(IP_X_FORWARDED_FOR_MULTIPLE);
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq(TEST_USERNAME),
                eq(INVOICE_NORMAL),
                eq(IP_X_FORWARDED_FOR_SINGLE),
                anyString(),
                anyMap()
        );
    }

    @Test
    void validateInvoice_ShouldExtractIpFromXRealIpHeader() {
        when(httpServletRequest.getHeader(HEADER_X_FORWARDED_FOR)).thenReturn(null);
        when(httpServletRequest.getHeader(HEADER_X_REAL_IP)).thenReturn(IP_X_REAL_IP);
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq(TEST_USERNAME),
                eq(INVOICE_NORMAL),
                eq(IP_X_REAL_IP),
                anyString(),
                anyMap()
        );
    }

    @Test
    void validateInvoice_ShouldFallbackToRemoteAddrWhenNoProxyHeaders() {
        when(httpServletRequest.getHeader(HEADER_X_FORWARDED_FOR)).thenReturn(null);
        when(httpServletRequest.getHeader(HEADER_X_REAL_IP)).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn(IP_FALLBACK);
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq(TEST_USERNAME),
                eq(INVOICE_NORMAL),
                eq(IP_FALLBACK),
                anyString(),
                anyMap()
        );
    }

    @Test
    void validateInvoice_ShouldHandleMissingUserAgent() {
        when(httpServletRequest.getHeader(HEADER_USER_AGENT)).thenReturn(null);
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq(TEST_USERNAME),
                eq(INVOICE_NORMAL),
                anyString(),
                eq(USER_AGENT_UNKNOWN),
                anyMap()
        );
    }

    @Test
    void validateInvoice_ShouldLogCorrectAuditDetails() {
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq(TEST_USERNAME),
                eq(INVOICE_NORMAL),
                anyString(),
                anyString(),
                argThat((Map<String, Object> details) ->
                        details.containsKey(DETAIL_KEY_IBAN) &&
                        details.containsKey(DETAIL_KEY_AMOUNT) &&
                        details.containsKey(DETAIL_KEY_VENDOR_ID) &&
                        details.get(DETAIL_KEY_IBAN).equals(VALID_BULGARIAN_IBAN) &&
                        details.get(DETAIL_KEY_AMOUNT).equals(NORMAL_AMOUNT.toString()) &&
                        details.get(DETAIL_KEY_VENDOR_ID).equals(TEST_VENDOR_ID)
                )
        );
    }

    @Test
    void validateInvoice_WithReviewDecision_ShouldReturnCorrectResponse() {
        FraudCheckResponse reviewResponse = new FraudCheckResponse(
                FraudCheckResponse.FraudDecision.REVIEW,
                SCORE_MEDIUM_RISK,
                List.of(RISK_AMOUNT_THRESHOLD)
        );

        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(reviewResponse);

        ResponseEntity<FraudCheckResponse> response = invoiceProxyController.validateInvoice(
                validRequest, authentication, httpServletRequest
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(FraudCheckResponse.FraudDecision.REVIEW);
        assertThat(response.getBody().fraudScore()).isEqualTo(SCORE_MEDIUM_RISK);
    }
}
