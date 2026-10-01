package org.brite.banking.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.TokenClaims;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.service.JwtService;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;

/**
 * Requires a valid employee JWT ({@code Authorization: Bearer <token>} from {@code POST /v1/api/staff/login}) on every
 * staff endpoint except the login itself. On success the token's subject (the employee number) is placed in the
 * request attribute {@link #EMPLOYEE_ATTRIBUTE}, which is the only place the staff controllers take the acting
 * employee from - there is no header to forge any more.
 * <p>
 * The token only proves who logged in. It is deliberately <em>not</em> trusted for permissions or status: every
 * request still goes through {@code EmployeeService.requirePrivilege}, which reloads the employee, their login status
 * and their role, so a suspension or demotion takes effect immediately instead of when the token expires.
 * <p>
 * Like the rate limiter this runs before DispatcherServlet, so it writes the plain-text response itself.
 */
@Slf4j
@RequiredArgsConstructor
public class StaffAuthenticationFilter extends OncePerRequestFilter {
    public static final String EMPLOYEE_ATTRIBUTE = "banking.authenticatedEmployee";
    static final String LOGIN_PATH = "/v1/api/staff/login";
    private static final String BEARER = "bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod()) && request.getHeader("Access-Control-Request-Method") != null) {
            filterChain.doFilter(request, response);
            return;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (LOGIN_PATH.equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.toLowerCase(Locale.ROOT).startsWith(BEARER) || header.substring(BEARER.length()).isBlank()) {
            log.warn(BankingMessages.LOG_STAFF_AUTH_MISSING, path);
            reject(response, HttpStatus.UNAUTHORIZED, BankingMessages.AUTHENTICATION_REQUIRED);
            return;
        }
        TokenClaims claims;
        try {
            claims = jwtService.parse(header.substring(BEARER.length()).trim());
        } catch (InvalidTokenException e) {
            log.warn(BankingMessages.LOG_STAFF_AUTH_REJECTED, path);
            reject(response, HttpStatus.UNAUTHORIZED, BankingMessages.INVALID_TOKEN);
            return;
        }
        if (!"employee".equals(claims.getType())) {
            log.warn(BankingMessages.LOG_STAFF_AUTH_WRONG_TYPE, path, claims.getType());
            reject(response, HttpStatus.FORBIDDEN, BankingMessages.EMPLOYEE_TOKEN_REQUIRED);
            return;
        }
        request.setAttribute(EMPLOYEE_ATTRIBUTE, claims.getSubject());
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
