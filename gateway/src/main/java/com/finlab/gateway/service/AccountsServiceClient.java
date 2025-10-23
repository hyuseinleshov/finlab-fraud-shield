package com.finlab.gateway.service;

import com.finlab.gateway.dto.FraudCheckRequest;
import com.finlab.gateway.dto.FraudCheckResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client service for communicating with the Accounts microservice.
 * Handles fraud validation requests and automatically adds required API key authentication.
 */
@Service
public class AccountsServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(AccountsServiceClient.class);

    private final RestClient restClient;
    private final String accountsServiceUrl;
    private final String apiKey;

    public AccountsServiceClient(
            RestClient restClient,
            @Value("${accounts.service.url}") String accountsServiceUrl,
            @Value("${accounts.api.key}") String apiKey) {
        this.restClient = restClient;
        this.accountsServiceUrl = accountsServiceUrl;
        this.apiKey = apiKey;
    }

    /**
     * Validates an invoice for fraud by forwarding the request to Accounts service.
     *
     * @param request the fraud check request containing IBAN, amount, vendor, and invoice details
     * @return fraud check response with decision, score, and risk factors
     * @throws org.springframework.web.client.RestClientException if communication fails
     */
    public FraudCheckResponse validateInvoice(FraudCheckRequest request) {
        logger.debug("Forwarding fraud validation request to Accounts service: iban={}, amount={}, vendorId={}, invoiceNumber={}",
                request.iban(), request.amount(), request.vendorId(), request.invoiceNumber());

        try {
            FraudCheckResponse response = restClient.post()
                    .uri(accountsServiceUrl + "/api/v1/invoices/validate")
                    .header("X-API-KEY", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FraudCheckResponse.class);

            if (response != null) {
                logger.info("Fraud validation completed: decision={}, score={}, riskFactors={}",
                        response.decision(), response.fraudScore(), response.riskFactors());
            }

            return response;
        } catch (Exception e) {
            logger.error("Failed to communicate with Accounts service", e);
            throw e;
        }
    }
}
