package org.brite.banking.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.RateLimitDecision;
import org.brite.banking.domain.TokenClaims;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.service.CustomerQuotaService;
import org.brite.banking.service.EmployeeQuotaService;
import org.brite.banking.service.JwtService;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Lightweight API-gateway ingress layer for the banking module: every request to a
 * banking controller (see url-patterns in {@link BankingGatewayConfig}) must carry a
 * customer identifier header, and is capped per day. A request that carries a valid customer token is counted against
 * <em>that customer</em> (the token's subject, never the header, which anyone can change) in {@code customer_rate_limits}
 * ({@link CustomerQuotaService}: the customer's own limit, else {@link RateLimitProperties#getRequestsPerDay()}); one with a valid employee token
 * is counted against <em>that employee</em> in {@code employee_rate_limits} ({@link EmployeeQuotaService}: the employee's own limit, else
 * {@link RateLimitProperties#getEmployeeRequestsPerDay()}). Every other request
 * (no token yet: login, registration, password reset, locations, and invalid tokens) is counted per header value in memory
 * ({@link CustomerRateLimiter}). Requests never reach DispatcherServlet/controllers
 * once rejected here, matching how a real gateway would shed load before the backend.
 */
@Slf4j
@RequiredArgsConstructor
public class BankingRateLimitFilter extends OncePerRequestFilter {
    private static final String RATE_LIMIT_LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String RATE_LIMIT_REMAINING_HEADER = "X-RateLimit-Remaining";

    private final RateLimitProperties rateLimitProperties;
    private final CustomerRateLimiter customerRateLimiter;
    private final JwtService jwtService;
    private final CustomerQuotaService customerQuotaService;
    private final EmployeeQuotaService employeeQuotaService;

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

        TokenClaims claims = verifiedClaims(request);
        RateLimitDecision decision;
        if (claims != null && "customer".equals(claims.getType()) && isNumber(claims.getSubject())) {
            customerId = claims.getSubject();
            decision = customerQuotaService.consumeRequest(Long.valueOf(claims.getSubject()));
        } else if (claims != null && "employee".equals(claims.getType()) && claims.getSubject() != null && !claims.getSubject().isBlank()) {
            customerId = claims.getSubject();
            decision = employeeQuotaService.consumeRequest(claims.getSubject());
        } else {
            int left = customerRateLimiter.tryConsume(customerId);
            int limit = rateLimitProperties.getRequestsPerDay();
            decision = new RateLimitDecision(left >= 0, limit, Math.max(left, 0));
        }
        if (!decision.allowed()) {
            log.warn(BankingMessages.LOG_RATE_LIMIT_EXCEEDED, customerId, request.getRequestURI(), decision.limit());
            response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(decision.limit()));
            response.setHeader(RATE_LIMIT_REMAINING_HEADER, "0");
            respond(response, HttpStatus.TOO_MANY_REQUESTS,
                    String.format(BankingMessages.RATE_LIMIT_DAILY_LIMIT_EXCEEDED, customerId, decision.limit()));
            return;
        }

        response.setHeader(RATE_LIMIT_LIMIT_HEADER, String.valueOf(decision.limit()));
        response.setHeader(RATE_LIMIT_REMAINING_HEADER, String.valueOf(decision.remaining()));
        filterChain.doFilter(request, response);
    }

    private static boolean isNumber(String value) {
        try {
            Long.valueOf(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * The claims of a valid bearer token, or null when there is no token or it is invalid. Only the signature, expiry and type are checked here;
     * whether the login is still allowed is the authentication filters' job, which run next.
     */
    private TokenClaims verifiedClaims(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.toLowerCase(Locale.ROOT).startsWith("bearer ") || header.substring(7).isBlank()) {
            return null;
        }
        try {
            return jwtService.parse(header.substring(7).trim());
        } catch (InvalidTokenException e) {
            return null;
        }
    }

    private void respond(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
