package com.finlab.accounts.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ApiKeyAuthenticationFilter.
 *
 * Tests validation of X-API-KEY header for service-to-service authentication.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    private ApiKeyAuthenticationFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private static final String VALID_API_KEY = "test-api-key-12345";
    private static final String INVALID_API_KEY = "wrong-api-key";
    private static final String API_KEY_HEADER = "X-API-KEY";

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter();
        ReflectionTestUtils.setField(filter, "expectedApiKey", VALID_API_KEY);
    }

    @Test
    void doFilterInternal_WithValidApiKey_ShouldProceed() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/v1/invoices/validate");
        when(request.getHeader(API_KEY_HEADER)).thenReturn(VALID_API_KEY);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void doFilterInternal_WithMissingApiKey_ShouldReturn401() throws ServletException, IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        when(request.getRequestURI()).thenReturn("/api/v1/invoices/validate");
        when(request.getHeader(API_KEY_HEADER)).thenReturn(null);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WithEmptyApiKey_ShouldReturn401() throws ServletException, IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        when(request.getRequestURI()).thenReturn("/api/v1/invoices/validate");
        when(request.getHeader(API_KEY_HEADER)).thenReturn("   ");
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WithInvalidApiKey_ShouldReturn401() throws ServletException, IOException {
        StringWriter stringWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(stringWriter);

        when(request.getRequestURI()).thenReturn("/api/v1/invoices/validate");
        when(request.getHeader(API_KEY_HEADER)).thenReturn(INVALID_API_KEY);
        when(response.getWriter()).thenReturn(writer);

        filter.doFilterInternal(request, response, filterChain);

        verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        verify(response).setContentType("application/json");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilterInternal_WithHealthEndpoint_ShouldAllowWithoutApiKey() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
        verify(request, never()).getHeader(anyString());
    }
}
