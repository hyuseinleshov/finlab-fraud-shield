package com.finlab.gateway.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.lang.NonNull;

/**
 * Utility for extracting client information from HTTP requests.
 * Centralizes IP and user agent extraction logic to maintain consistency across controllers.
 */
public final class HttpRequestUtils {

    private static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String HEADER_X_REAL_IP = "X-Real-IP";
    private static final String HEADER_USER_AGENT = "User-Agent";
    private static final String UNKNOWN = "unknown";

    private HttpRequestUtils() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Extracts client IP address, checking proxy headers first.
     *
     * @param request HTTP servlet request
     * @return client IP address or "unknown"
     */
    @NonNull
    public static String extractClientIp(@NonNull HttpServletRequest request) {
        String ip = request.getHeader(HEADER_X_FORWARDED_FOR);

        if (ip != null && !ip.isEmpty()) {
            // Take first IP from comma-separated list (original client)
            if (ip.contains(",")) {
                ip = ip.split(",")[0].trim();
            }
            return ip;
        }

        ip = request.getHeader(HEADER_X_REAL_IP);
        if (ip != null && !ip.isEmpty()) {
            return ip;
        }

        ip = request.getRemoteAddr();
        return (ip != null && !ip.isEmpty()) ? ip : UNKNOWN;
    }

    /**
     * Extracts user agent string from request headers.
     *
     * @param request HTTP servlet request
     * @return user agent string or "unknown"
     */
    @NonNull
    public static String extractUserAgent(@NonNull HttpServletRequest request) {
        String userAgent = request.getHeader(HEADER_USER_AGENT);
        return (userAgent != null && !userAgent.isEmpty()) ? userAgent : UNKNOWN;
    }
}
