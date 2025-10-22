package com.finlab.accounts.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finlab.accounts.dto.FraudCheckRequest;
import com.finlab.accounts.dto.FraudCheckResponse;
import com.finlab.accounts.exception.GlobalExceptionHandler;
import com.finlab.accounts.service.FraudScoringEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FraudController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FraudControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FraudScoringEngine fraudScoringEngine;

    @Test
    void validateInvoice_WithValidRequest_ShouldReturnAllowDecision() throws Exception {
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("1500.00"),
            1L,
            "INV-001"
        );

        FraudCheckResponse expectedResponse = new FraudCheckResponse(
            FraudCheckResponse.FraudDecision.ALLOW,
            0,
            List.of()
        );

        when(fraudScoringEngine.checkFraud(any(FraudCheckRequest.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/invoices/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("ALLOW"))
            .andExpect(jsonPath("$.fraudScore").value(0))
            .andExpect(jsonPath("$.riskFactors").isArray())
            .andExpect(jsonPath("$.riskFactors").isEmpty());
    }

    @Test
    void validateInvoice_WithFraudulentRequest_ShouldReturnBlockDecision() throws Exception {
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG99INVALID00000000000",
            new BigDecimal("1500.00"),
            1L,
            "INV-FRAUD"
        );

        FraudCheckResponse expectedResponse = new FraudCheckResponse(
            FraudCheckResponse.FraudDecision.BLOCK,
            100,
            List.of("Duplicate invoice detected within 24 hours", "Invalid IBAN: Invalid checksum")
        );

        when(fraudScoringEngine.checkFraud(any(FraudCheckRequest.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/invoices/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("BLOCK"))
            .andExpect(jsonPath("$.fraudScore").value(100))
            .andExpect(jsonPath("$.riskFactors").isArray())
            .andExpect(jsonPath("$.riskFactors.length()").value(2));
    }

    @Test
    void validateInvoice_WithMissingIban_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String requestJson = """
            {
                "amount": 1500.00,
                "vendorId": 1,
                "invoiceNumber": "INV-001"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/invoices/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.errors.iban").exists());
    }

    @Test
    void validateInvoice_WithNegativeAmount_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String requestJson = """
            {
                "iban": "BG80BNBG96611020345678",
                "amount": -100.00,
                "vendorId": 1,
                "invoiceNumber": "INV-001"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/invoices/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    void validateInvoice_WithNullVendorId_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String requestJson = """
            {
                "iban": "BG80BNBG96611020345678",
                "amount": 1500.00,
                "invoiceNumber": "INV-001"
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/invoices/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.vendorId").exists());
    }

    @Test
    void validateInvoice_WithEmptyInvoiceNumber_ShouldReturnBadRequest() throws Exception {
        // Arrange
        String requestJson = """
            {
                "iban": "BG80BNBG96611020345678",
                "amount": 1500.00,
                "vendorId": 1,
                "invoiceNumber": ""
            }
            """;

        // Act & Assert
        mockMvc.perform(post("/api/v1/invoices/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.errors.invoiceNumber").exists());
    }

    @Test
    void validateInvoice_WithReviewDecision_ShouldReturnReviewStatus() throws Exception {
        // Arrange
        FraudCheckRequest request = new FraudCheckRequest(
            "BG80BNBG96611020345678",
            new BigDecimal("4990.00"),
            1L,
            "INV-REVIEW"
        );

        FraudCheckResponse expectedResponse = new FraudCheckResponse(
            FraudCheckResponse.FraudDecision.REVIEW,
            50,
            List.of("Duplicate invoice detected within 24 hours")
        );

        when(fraudScoringEngine.checkFraud(any(FraudCheckRequest.class)))
            .thenReturn(expectedResponse);

        // Act & Assert
        mockMvc.perform(post("/api/v1/invoices/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.decision").value("REVIEW"))
            .andExpect(jsonPath("$.fraudScore").value(50))
            .andExpect(jsonPath("$.riskFactors.length()").value(1));
    }

    @Test
    void health_ShouldReturnOkStatus() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/invoices/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.message").value("Fraud detection service operational"));
    }
}
