package org.brite.banking.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.TokenClaims;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.JwtService;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

/**
 * Requires a valid customer JWT ({@code Authorization: Bearer <token>} from {@code POST /v1/api/customers/login}) on the
 * customer-facing account, portal and customer-credential endpoints, except those a user without a token must reach: the banking registration
 * ({@code POST /v1/api/accounts/newaccount}), the login and the
 * password-reset calls (on both the banking API and the portal BFF). On success the customer id (the token's subject) is placed in the request
 * attribute {@link #CUSTOMER_ATTRIBUTE}, the only place the customer handlers take it from.
 * <p>
 * The token only proves who logged in: the customer's login is re-checked on every request, so suspending or locking a
 * login stops it at once instead of when the token expires, and a token issued before the password last changed (a reset) is refused. Which accounts the customer may touch is decided by
 * {@code CustomerAccessService} in the handlers. An employee token is refused here (staff use the staff endpoints).
 * Writes the plain-text response itself, like the other gateway filters.
 */
@Slf4j
@RequiredArgsConstructor
public class CustomerAuthenticationFilter extends OncePerRequestFilter {
    public static final String CUSTOMER_ATTRIBUTE = "banking.authenticatedCustomer";
    /** Method + path (inside the context path) of the endpoints that create a customer and so can't require a login. */
    static final Set<String> OPEN = Set.of("POST /v1/api/accounts/newaccount",
            "POST /v1/api/customers/login", "POST /v1/api/customers/password-reset/questions", "POST /v1/api/customers/password-reset",
            "POST /bff/v1/portal/login", "GET /bff/v1/portal/security-questions/catalog",
            "POST /bff/v1/portal/password-reset/questions", "POST /bff/v1/portal/password-reset");
    private static final String BEARER = "bearer ";

    private final JwtService jwtService;
    private final CustomerCredentialService customerCredentialService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) && request.getHeader("Access-Control-Request-Method") != null) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (OPEN.contains(request.getMethod() + " " + path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.toLowerCase(Locale.ROOT).startsWith(BEARER) || header.substring(BEARER.length()).isBlank()) {
            log.warn(BankingMessages.LOG_CUSTOMER_AUTH_MISSING, path);
            reject(response, HttpStatus.UNAUTHORIZED, BankingMessages.CUSTOMER_AUTHENTICATION_REQUIRED);
            return;
        }
        TokenClaims claims;
        try {
            claims = jwtService.parse(header.substring(BEARER.length()).trim());
        } catch (InvalidTokenException e) {
            log.warn(BankingMessages.LOG_CUSTOMER_AUTH_REJECTED, path);
            reject(response, HttpStatus.UNAUTHORIZED, BankingMessages.INVALID_TOKEN);
            return;
        }
        if (!"customer".equals(claims.getType())) {
            log.warn(BankingMessages.LOG_CUSTOMER_AUTH_WRONG_TYPE, path, claims.getType());
            reject(response, HttpStatus.FORBIDDEN, BankingMessages.CUSTOMER_TOKEN_REQUIRED);
            return;
        }
        Long customerId;
        try {
            customerId = Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            log.warn(BankingMessages.LOG_CUSTOMER_AUTH_REJECTED, path);
            reject(response, HttpStatus.UNAUTHORIZED, BankingMessages.INVALID_TOKEN);
            return;
        }
        try {
            customerCredentialService.requireActiveLogin(customerId, claims.getIssuedAt());
        } catch (LoginNotActiveException | EmployeeLockedException e) {
            log.warn(BankingMessages.LOG_CUSTOMER_AUTH_LOGIN_INACTIVE, customerId, path);
            reject(response, HttpStatus.FORBIDDEN, e.getMessage());
            return;
        } catch (InvalidCredentialsException | InvalidTokenException e) {
            log.warn(BankingMessages.LOG_CUSTOMER_AUTH_REJECTED, path);
            reject(response, HttpStatus.UNAUTHORIZED, BankingMessages.INVALID_TOKEN);
            return;
        }
        request.setAttribute(CUSTOMER_ATTRIBUTE, customerId);
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        if (status == HttpStatus.UNAUTHORIZED) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        response.setStatus(status.value());
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter().write(message);
    }
}
