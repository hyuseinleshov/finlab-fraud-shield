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
                "BG80BNBG96611020345678",
                new BigDecimal("1500.00"),
                1L,
                "INV-2025-001"
        );

        mockResponse = new FraudCheckResponse(
                FraudCheckResponse.FraudDecision.ALLOW,
                15,
                List.of("Low risk transaction")
        );

        when(authentication.getName()).thenReturn("testuser");
        when(httpServletRequest.getRemoteAddr()).thenReturn("192.168.1.100");
        when(httpServletRequest.getHeader("User-Agent")).thenReturn("Mozilla/5.0");
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
    }

    @Test
    void validateInvoice_WithValidRequest_ShouldReturnSuccessResponse() {
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        ResponseEntity<FraudCheckResponse> response = invoiceProxyController.validateInvoice(
                validRequest, authentication, httpServletRequest
        );

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(FraudCheckResponse.FraudDecision.ALLOW);
        assertThat(response.getBody().fraudScore()).isEqualTo(15);

        verify(accountsServiceClient, times(1)).validateInvoice(validRequest);
        verify(auditLogRepository, times(1)).logInvoiceValidation(
                eq("testuser"),
                eq("INV-2025-001"),
                anyString(),
                anyString(),
                anyMap()
        );
    }

    @Test
    void validateInvoice_WithHighRiskInvoice_ShouldReturnBlockDecision() {
        FraudCheckResponse blockResponse = new FraudCheckResponse(
                FraudCheckResponse.FraudDecision.BLOCK,
                85,
                List.of("Duplicate invoice detected", "Invalid IBAN")
        );

        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(blockResponse);

        ResponseEntity<FraudCheckResponse> response = invoiceProxyController.validateInvoice(
                validRequest, authentication, httpServletRequest
        );

        assertThat(response.getStatusCodeValue()).isEqualTo(200);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(FraudCheckResponse.FraudDecision.BLOCK);
        assertThat(response.getBody().fraudScore()).isEqualTo(85);
        assertThat(response.getBody().riskFactors()).hasSize(2);
    }

    @Test
    void validateInvoice_ShouldExtractIpFromXForwardedForHeader() {
        when(httpServletRequest.getHeader("X-Forwarded-For"))
                .thenReturn("203.0.113.195, 198.51.100.178");
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq("testuser"),
                eq("INV-2025-001"),
                eq("203.0.113.195"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void validateInvoice_ShouldExtractIpFromXRealIpHeader() {
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn("203.0.113.42");
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq("testuser"),
                eq("INV-2025-001"),
                eq("203.0.113.42"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void validateInvoice_ShouldFallbackToRemoteAddrWhenNoProxyHeaders() {
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq("testuser"),
                eq("INV-2025-001"),
                eq("10.0.0.5"),
                anyString(),
                anyMap()
        );
    }

    @Test
    void validateInvoice_ShouldHandleMissingUserAgent() {
        when(httpServletRequest.getHeader("User-Agent")).thenReturn(null);
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq("testuser"),
                eq("INV-2025-001"),
                anyString(),
                eq("Unknown"),
                anyMap()
        );
    }

    @Test
    void validateInvoice_ShouldLogCorrectAuditDetails() {
        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(mockResponse);

        invoiceProxyController.validateInvoice(validRequest, authentication, httpServletRequest);

        verify(auditLogRepository).logInvoiceValidation(
                eq("testuser"),
                eq("INV-2025-001"),
                anyString(),
                anyString(),
                argThat((Map<String, Object> details) ->
                        details.containsKey("iban") &&
                        details.containsKey("amount") &&
                        details.containsKey("vendorId") &&
                        details.get("iban").equals("BG80BNBG96611020345678") &&
                        details.get("amount").equals("1500.00") &&
                        details.get("vendorId").equals(1L)
                )
        );
    }

    @Test
    void validateInvoice_WithReviewDecision_ShouldReturnCorrectResponse() {
        FraudCheckResponse reviewResponse = new FraudCheckResponse(
                FraudCheckResponse.FraudDecision.REVIEW,
                45,
                List.of("Amount near threshold")
        );

        when(accountsServiceClient.validateInvoice(any(FraudCheckRequest.class)))
                .thenReturn(reviewResponse);

        ResponseEntity<FraudCheckResponse> response = invoiceProxyController.validateInvoice(
                validRequest, authentication, httpServletRequest
        );

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().decision()).isEqualTo(FraudCheckResponse.FraudDecision.REVIEW);
        assertThat(response.getBody().fraudScore()).isEqualTo(45);
    }
}
