package org.brite.banking.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.brite.banking.domain.AuthenticatedCustomer;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.domain.RateLimitDecision;
import org.brite.banking.rules.JwtProperties;
import org.brite.banking.service.CustomerQuotaService;
import org.brite.banking.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain unit test (no Spring context) for the banking API-gateway rate-limit filter,
 * using mocked servlet objects so no real HTTP round trip is needed.
 */
@ExtendWith(MockitoExtension.class)
class BankingRateLimitFilterTest {

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private BankingRateLimitFilter filter;
    private CustomerQuotaService quotaService;
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = RateLimitProperties.builder()
                .enabled(true)
                .requestsPerDay(1)
                .customerHeaderName("X-Customer-Id")
                .build();
        quotaService = mock(CustomerQuotaService.class);
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setSecret("rate-limit-test-secret-at-least-32-chars!");
        jwtService = new JwtService(jwtProperties);
        filter = new BankingRateLimitFilter(properties, new CustomerRateLimiter(properties), jwtService, quotaService);
    }

    @Test
    void doFilter_missingCustomerHeader_rejectsWithBadRequestAndDoesNotChain() throws Exception {
        when(request.getHeader("X-Customer-Id")).thenReturn(null);
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(400);
        assertThat(body.toString()).contains("X-Customer-Id");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_withinDailyLimit_chainsAndSetsRateLimitHeaders() throws Exception {
        when(request.getHeader("X-Customer-Id")).thenReturn("cust-1");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response).setHeader("X-RateLimit-Limit", "1");
        verify(response).setHeader("X-RateLimit-Remaining", "0");
    }

    @Test
    void doFilter_dailyLimitAlreadyExhausted_rejectsWithTooManyRequests() throws Exception {
        when(request.getHeader("X-Customer-Id")).thenReturn("cust-1");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, filterChain); // consumes the only allowed request
        filter.doFilter(request, response, filterChain); // should now be rejected

        verify(response).setStatus(429);
        assertThat(body.toString()).contains("cust-1");
        verify(filterChain, org.mockito.Mockito.times(1)).doFilter(request, response);
    }

    @Test
    void doFilter_corsPreflight_chainsWithoutRequiringCustomerHeaderOrConsumingQuota() throws Exception {
        when(request.getMethod()).thenReturn("OPTIONS");
        when(request.getHeader("Access-Control-Request-Method")).thenReturn("GET");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(response, never()).setStatus(400);
    }

    private String customerToken(long customerId) {
        return "Bearer " + jwtService.issueCustomerToken(AuthenticatedCustomer.builder().customerId(customerId).firstName("A").lastName("B").build()).getToken();
    }

    @Test
    void doFilter_validCustomerToken_countsAgainstTheTokensCustomerNotTheHeader() throws Exception {
        when(request.getHeader("X-Customer-Id")).thenReturn("whatever-the-caller-typed");
        when(request.getHeader("Authorization")).thenReturn(customerToken(7));
        when(quotaService.consumeRequest(7L)).thenReturn(new RateLimitDecision(true, 50, 49));

        filter.doFilter(request, response, filterChain);

        verify(quotaService).consumeRequest(7L);
        verify(response).setHeader("X-RateLimit-Limit", "50");
        verify(response).setHeader("X-RateLimit-Remaining", "49");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_changingTheHeaderDoesNotEscapeTheTokensCustomersQuota() throws Exception {
        when(request.getHeader("X-Customer-Id")).thenReturn("first", "second", "third");
        when(request.getHeader("Authorization")).thenReturn(customerToken(7));
        when(quotaService.consumeRequest(7L)).thenReturn(new RateLimitDecision(true, 1000, 999), new RateLimitDecision(true, 1000, 998),
                new RateLimitDecision(true, 1000, 997));

        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verify(quotaService, org.mockito.Mockito.times(3)).consumeRequest(7L);
    }

    @Test
    void doFilter_tokenCustomerOverTheirOwnLimit_getsTooManyRequestsWithTheirLimit() throws Exception {
        when(request.getHeader("X-Customer-Id")).thenReturn("cust-1");
        when(request.getHeader("Authorization")).thenReturn(customerToken(7));
        when(quotaService.consumeRequest(7L)).thenReturn(new RateLimitDecision(false, 5, 0));
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        filter.doFilter(request, response, filterChain);

        verify(response).setStatus(429);
        verify(response).setHeader("X-RateLimit-Limit", "5");
        verify(response).setHeader("X-RateLimit-Remaining", "0");
        assertThat(body.toString()).contains("customer 7").contains("max 5");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_noTokenInvalidTokenOrEmployeeToken_fallBackToTheHeaderCounter() throws Exception {
        when(request.getHeader("X-Customer-Id")).thenReturn("anon-1", "anon-2", "anon-3");
        Employee employee = Employee.builder().employeeNumber("EMP-000010").role(EmployeeRole.TELLER).status(EmployeeStatus.ACTIVE).build();
        when(request.getHeader("Authorization")).thenReturn(null, "Bearer not.a.token",
                "Bearer " + jwtService.issueEmployeeToken(employee).getToken());

        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);
        filter.doFilter(request, response, filterChain);

        verifyNoInteractions(quotaService);
        verify(filterChain, org.mockito.Mockito.times(3)).doFilter(request, response);
    }
}
