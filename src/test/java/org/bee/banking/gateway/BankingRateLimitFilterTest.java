package org.bee.banking.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
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

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = RateLimitProperties.builder()
                .enabled(true)
                .requestsPerDay(1)
                .customerHeaderName("X-Customer-Id")
                .build();
        filter = new BankingRateLimitFilter(properties, new CustomerRateLimiter(properties));
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
}
