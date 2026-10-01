package org.brite.banking.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.messages.BankingMessages;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lightweight API-gateway ingress layer for the banking module: every request to a
 * banking controller (see url-patterns in {@link BankingGatewayConfig}) must carry a
 * customer identifier header, and is capped at {@link RateLimitProperties#getRequestsPerDay()}
 * requests per day for that customer. Requests never reach DispatcherServlet/controllers
 * once rejected here, matching how a real gateway would shed load before the backend.
 */
@Slf4j
@RequiredArgsConstructor
public class BankingRateLimitFilter extends OncePerRequestFilter {
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";

    private final RateLimitProperties rateLimitProperties;
    private final CustomerRateLimiter customerRateLimiter;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // CORS preflights can't carry custom headers; let them through so the CORS mapping can answer.
        if (HttpMethod.OPTIONS.matches(request.getMethod()) && request.getHeader("Access-Control-Request-Method") != null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!rateLimitProperties.isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        String customerId = request.getHeader(rateLimitProperties.getCustomerHeaderName());
        if (customerId == null || customerId.isBlank()) {
            log.warn(BankingMessages.LOG_RATE_LIMIT_HEADER_MISSING, request.getRequestURI(), rateLimitProperties.getCustomerHeaderName());
            respond(response, HttpStatus.BAD_REQUEST,
                    String.format(BankingMessages.RATE_LIMIT_CUSTOMER_HEADER_REQUIRED, rateLimitProperties.getCustomerHeaderName()));
            return;
        }

        int remaining = customerRateLimiter.tryConsume(customerId);
        if (remaining < 0) {
            log.warn(BankingMessages.LOG_RATE_LIMIT_EXCEEDED, customerId, request.getRequestURI(), rateLimitProperties.getRequestsPerDay());
            response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(rateLimitProperties.getRequestsPerDay()));
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, "0");
            respond(response, HttpStatus.TOO_MANY_REQUESTS,
                    String.format(BankingMessages.RATE_LIMIT_DAILY_LIMIT_EXCEEDED, customerId, rateLimitProperties.getRequestsPerDay()));
            return;
        }

        response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(rateLimitProperties.getRequestsPerDay()));
        response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(remaining));
        filterChain.doFilter(request, response);
    }

    private void respond(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
