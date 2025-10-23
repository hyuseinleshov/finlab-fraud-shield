package com.finlab.accounts.controller;

import com.finlab.accounts.dto.FraudCheckRequest;
import com.finlab.accounts.dto.FraudCheckResponse;
import com.finlab.accounts.service.FraudScoringEngine;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for fraud detection endpoints.
 * Validates invoice payment requests and returns fraud assessment.
 */
@RestController
@RequestMapping("/api/v1/invoices")
public class FraudController {

    private static final Logger log = LoggerFactory.getLogger(FraudController.class);

    private final FraudScoringEngine fraudScoringEngine;

    public FraudController(FraudScoringEngine fraudScoringEngine) {
        this.fraudScoringEngine = fraudScoringEngine;
    }

    /**
     * Validates invoice payment request for fraud.
     * Performance target: sub-200ms P95 response time.
     *
     * @param request fraud check request containing transaction details
     * @return fraud assessment with decision, score, and risk factors
     */
    @PostMapping("/validate")
    public ResponseEntity<FraudCheckResponse> validateInvoice(
        @Valid @RequestBody FraudCheckRequest request
    ) {
        long startTime = System.currentTimeMillis();

        log.info("Received fraud check request for invoice: {}", request.invoiceNumber());

        try {
            FraudCheckResponse response = fraudScoringEngine.checkFraud(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("Fraud check completed in {}ms: decision={}, score={}",
                duration, response.decision(), response.fraudScore());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Fraud check failed after {}ms for invoice: {}",
                duration, request.invoiceNumber(), e);
            throw e;
        }
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        return ResponseEntity.ok(new HealthResponse("ok", "Fraud detection service operational"));
    }

    public record HealthResponse(String status, String message) {}
}
