package com.finlab.accounts.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * API key authentication filter for service-to-service communication.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final String HEALTH_ENDPOINT = "/actuator/health";

    @Value("${security.api-key}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Allow health check endpoint without authentication
        if (requestPath.equals(HEALTH_ENDPOINT)) {
            logger.debug("Allowing unauthenticated access to health endpoint");
            filterChain.doFilter(request, response);
            return;
        }

        // Extract API key from header
        String providedApiKey = request.getHeader(API_KEY_HEADER);

        // Validate API key presence
        if (providedApiKey == null || providedApiKey.trim().isEmpty()) {
            logger.warn("Missing API key in request to {}", requestPath);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Missing X-API-KEY header\"}");
            return;
        }

        // Validate API key value
        if (!expectedApiKey.equals(providedApiKey)) {
            logger.warn("Invalid API key provided for request to {}", requestPath);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Invalid X-API-KEY\"}");
            return;
        }

        // API key is valid, set authentication in SecurityContext
        logger.debug("Valid API key authenticated for request to {}", requestPath);

        // Create authentication token and set in security context
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(
                "api-service", null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }
}
