package com.finlab.accounts.dto;

import java.util.List;

/**
 * Response DTO containing fraud detection results.
 */
public record FraudCheckResponse(
    FraudDecision decision,
    int fraudScore,
    List<String> riskFactors
) {
    public enum FraudDecision {
        ALLOW,   // Score 0-30
        REVIEW,  // Score 31-70
        BLOCK    // Score 71-100
    }
}
