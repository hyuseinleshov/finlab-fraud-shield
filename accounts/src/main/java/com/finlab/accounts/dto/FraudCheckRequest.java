package com.finlab.accounts.dto;

import java.math.BigDecimal;

/**
 * Request DTO for fraud detection validation.
 */
public record FraudCheckRequest(
    String iban,
    BigDecimal amount,
    Long vendorId,
    String invoiceNumber
) {
    public FraudCheckRequest {
        if (iban == null || iban.isBlank()) {
            throw new IllegalArgumentException("IBAN cannot be null or empty");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (vendorId == null) {
            throw new IllegalArgumentException("Vendor ID cannot be null");
        }
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            throw new IllegalArgumentException("Invoice number cannot be null or empty");
        }
    }
}
