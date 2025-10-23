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

    // Test data constants
    private static final String VALID_BULGARIAN_IBAN = "BG80BNBG96611020345678";
    private static final String INVALID_IBAN = "BG99INVALID00000000000";
    private static final BigDecimal NORMAL_AMOUNT = new BigDecimal("1500.00");
    private static final BigDecimal THRESHOLD_MANIPULATION_AMOUNT = new BigDecimal("4990.00");
    private static final BigDecimal NEGATIVE_AMOUNT = new BigDecimal("-100.00");
    private static final Long TEST_VENDOR_ID = 1L;
    private static final String INVOICE_NORMAL = "INV-001";
    private static final String INVOICE_FRAUD = "INV-FRAUD";
    private static final String INVOICE_REVIEW = "INV-REVIEW";
    private static final String EMPTY_STRING = "";

    // Fraud scores
    private static final int SCORE_ZERO = 0;
    private static final int SCORE_DUPLICATE_INVOICE = 50;
    private static final int SCORE_DUPLICATE_AND_INVALID_IBAN = 100;
    private static final int SCORE_MEDIUM_RISK = 85;

    // HTTP status codes
    private static final int HTTP_OK = 200;
    private static final int HTTP_BAD_REQUEST = 400;

    // API endpoints
    private static final String ENDPOINT_VALIDATE = "/api/v1/invoices/validate";
    private static final String ENDPOINT_HEALTH = "/api/v1/invoices/health";

    // JSON path constants
    private static final String JSON_PATH_DECISION = "$.decision";
    private static final String JSON_PATH_FRAUD_SCORE = "$.fraudScore";
    private static final String JSON_PATH_RISK_FACTORS = "$.riskFactors";
    private static final String JSON_PATH_STATUS = "$.status";
    private static final String JSON_PATH_MESSAGE = "$.message";
    private static final String JSON_PATH_DETAILS = "$.details";

    // Response messages
    private static final String MSG_VALIDATION_FAILED = "Validation failed";
    private static final String MSG_HEALTH_OK = "ok";
    private static final String MSG_HEALTH_OPERATIONAL = "Fraud detection service operational";

    // Risk factor messages
    private static final String RISK_DUPLICATE_INVOICE = "Duplicate invoice detected within 24 hours";
    private static final String RISK_INVALID_IBAN_CHECKSUM = "Invalid IBAN: Invalid checksum";
    private static final String RISK_INVALID_IBAN = "Invalid IBAN";
    private static final String RISK_AMOUNT_THRESHOLD = "Amount near threshold";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private FraudScoringEngine fraudScoringEngine;

    @Test
    void validateInvoice_WithValidRequest_ShouldReturnAllowDecision() throws Exception {
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            INVOICE_NORMAL
        );

        FraudCheckResponse expectedResponse = new FraudCheckResponse(
            FraudCheckResponse.FraudDecision.ALLOW,
            SCORE_ZERO,
            List.of()
        );

        when(fraudScoringEngine.checkFraud(any(FraudCheckRequest.class)))
            .thenReturn(expectedResponse);

        mockMvc.perform(post(ENDPOINT_VALIDATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_PATH_DECISION).value("ALLOW"))
            .andExpect(jsonPath(JSON_PATH_FRAUD_SCORE).value(SCORE_ZERO))
            .andExpect(jsonPath(JSON_PATH_RISK_FACTORS).isArray())
            .andExpect(jsonPath(JSON_PATH_RISK_FACTORS).isEmpty());
    }

    @Test
    void validateInvoice_WithFraudulentRequest_ShouldReturnBlockDecision() throws Exception {
        FraudCheckRequest request = new FraudCheckRequest(
            INVALID_IBAN,
            NORMAL_AMOUNT,
            TEST_VENDOR_ID,
            INVOICE_FRAUD
        );

        FraudCheckResponse expectedResponse = new FraudCheckResponse(
            FraudCheckResponse.FraudDecision.BLOCK,
            SCORE_DUPLICATE_AND_INVALID_IBAN,
            List.of(RISK_DUPLICATE_INVOICE, RISK_INVALID_IBAN_CHECKSUM)
        );

        when(fraudScoringEngine.checkFraud(any(FraudCheckRequest.class)))
            .thenReturn(expectedResponse);

        mockMvc.perform(post(ENDPOINT_VALIDATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_PATH_DECISION).value("BLOCK"))
            .andExpect(jsonPath(JSON_PATH_FRAUD_SCORE).value(SCORE_DUPLICATE_AND_INVALID_IBAN))
            .andExpect(jsonPath(JSON_PATH_RISK_FACTORS).isArray())
            .andExpect(jsonPath(JSON_PATH_RISK_FACTORS + ".length()").value(2));
    }

    @Test
    void validateInvoice_WithMissingIban_ShouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "amount": 1500.00,
                "vendorId": 1,
                "invoiceNumber": "INV-001"
            }
            """;

        mockMvc.perform(post(ENDPOINT_VALIDATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(HTTP_BAD_REQUEST))
            .andExpect(jsonPath(JSON_PATH_MESSAGE).value(MSG_VALIDATION_FAILED))
            .andExpect(jsonPath(JSON_PATH_DETAILS + ".iban").exists());
    }

    @Test
    void validateInvoice_WithNegativeAmount_ShouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "iban": "BG80BNBG96611020345678",
                "amount": -100.00,
                "vendorId": 1,
                "invoiceNumber": "INV-001"
            }
            """;

        mockMvc.perform(post(ENDPOINT_VALIDATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(HTTP_BAD_REQUEST))
            .andExpect(jsonPath(JSON_PATH_DETAILS + ".amount").exists());
    }

    @Test
    void validateInvoice_WithNullVendorId_ShouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "iban": "BG80BNBG96611020345678",
                "amount": 1500.00,
                "invoiceNumber": "INV-001"
            }
            """;

        mockMvc.perform(post(ENDPOINT_VALIDATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(HTTP_BAD_REQUEST))
            .andExpect(jsonPath(JSON_PATH_DETAILS + ".vendorId").exists());
    }

    @Test
    void validateInvoice_WithEmptyInvoiceNumber_ShouldReturnBadRequest() throws Exception {
        String requestJson = """
            {
                "iban": "BG80BNBG96611020345678",
                "amount": 1500.00,
                "vendorId": 1,
                "invoiceNumber": ""
            }
            """;

        mockMvc.perform(post(ENDPOINT_VALIDATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(HTTP_BAD_REQUEST))
            .andExpect(jsonPath(JSON_PATH_DETAILS + ".invoiceNumber").exists());
    }

    @Test
    void validateInvoice_WithReviewDecision_ShouldReturnReviewStatus() throws Exception {
        FraudCheckRequest request = new FraudCheckRequest(
            VALID_BULGARIAN_IBAN,
            THRESHOLD_MANIPULATION_AMOUNT,
            TEST_VENDOR_ID,
            INVOICE_REVIEW
        );

        FraudCheckResponse expectedResponse = new FraudCheckResponse(
            FraudCheckResponse.FraudDecision.REVIEW,
            SCORE_DUPLICATE_INVOICE,
            List.of(RISK_DUPLICATE_INVOICE)
        );

        when(fraudScoringEngine.checkFraud(any(FraudCheckRequest.class)))
            .thenReturn(expectedResponse);

        mockMvc.perform(post(ENDPOINT_VALIDATE)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_PATH_DECISION).value("REVIEW"))
            .andExpect(jsonPath(JSON_PATH_FRAUD_SCORE).value(SCORE_DUPLICATE_INVOICE))
            .andExpect(jsonPath(JSON_PATH_RISK_FACTORS + ".length()").value(1));
    }

    @Test
    void health_ShouldReturnOkStatus() throws Exception {
        mockMvc.perform(get(ENDPOINT_HEALTH))
            .andExpect(status().isOk())
            .andExpect(jsonPath(JSON_PATH_STATUS).value(MSG_HEALTH_OK))
            .andExpect(jsonPath(JSON_PATH_MESSAGE).value(MSG_HEALTH_OPERATIONAL));
    }
}
