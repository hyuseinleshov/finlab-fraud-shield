package com.finlab.gateway.controller;

import com.finlab.gateway.dto.FraudCheckRequest;
import com.finlab.gateway.dto.FraudCheckResponse;
import com.finlab.gateway.repository.AuditLogRepository;
import com.finlab.gateway.service.AccountsServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller that proxies invoice validation requests from authenticated users to the Accounts service.
 * Handles JWT authentication, audit logging, and service-to-service communication.
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceProxyController {

    private static final Logger logger = LoggerFactory.getLogger(InvoiceProxyController.class);

    private final AccountsServiceClient accountsServiceClient;
    private final AuditLogRepository auditLogRepository;

    public InvoiceProxyController(AccountsServiceClient accountsServiceClient, AuditLogRepository auditLogRepository) {
        this.accountsServiceClient = accountsServiceClient;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Validates an invoice for potential fraud.
     * Requires JWT authentication, logs the request to audit log, and forwards to Accounts service.
     *
     * @param request the fraud check request containing invoice details
     * @param authentication Spring Security authentication object containing user principal
     * @param httpRequest HTTP request to extract IP address and user agent
     * @return fraud check response with decision, score, and risk factors
     */
    @PostMapping("/validate")
    public ResponseEntity<FraudCheckResponse> validateInvoice(
            @Valid @RequestBody FraudCheckRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest) {

        String username = authentication.getName();
        String ipAddress = getClientIpAddress(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        logger.info("Invoice validation request from user: {}, invoice: {}, iban: {}, amount: {}",
                username, request.invoiceNumber(), request.iban(), request.amount());

        Map<String, Object> auditDetails = new HashMap<>();
        auditDetails.put("iban", request.iban());
        auditDetails.put("amount", request.amount().toString());
        auditDetails.put("vendorId", request.vendorId());

        auditLogRepository.logInvoiceValidation(
                username,
                request.invoiceNumber(),
                ipAddress,
                userAgent != null ? userAgent : "Unknown",
                auditDetails
        );

        FraudCheckResponse response = accountsServiceClient.validateInvoice(request);

        logger.info("Invoice validation completed for user: {}, invoice: {}, decision: {}, score: {}",
                username, request.invoiceNumber(), response.decision(), response.fraudScore());

        return ResponseEntity.ok(response);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }
}
