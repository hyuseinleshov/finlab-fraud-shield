package com.finlab.accounts.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * Request DTO for fraud detection validation.
 */
public record FraudCheckRequest(
    @NotBlank(message = "IBAN cannot be null or empty")
    String iban,

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be positive")
    BigDecimal amount,

    @NotNull(message = "Vendor ID cannot be null")
    @Positive(message = "Vendor ID must be positive")
    Long vendorId,

    @NotBlank(message = "Invoice number cannot be null or empty")
    String invoiceNumber
) {
}
