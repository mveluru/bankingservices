package org.brite.banking.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Stamps every banking request with a unique business transaction id (btid) before it
 * reaches any controller - the outermost gateway concern, running ahead of even
 * {@link BankingRateLimitFilter} (see ordering in {@link BankingGatewayConfig}) so
 * rate-limit rejections are correlated too. The id is stored in SLF4J's MDC under
 * {@link #MDC_KEY}, so every log statement in every layer (controller, service,
 * repository) for the lifetime of this request automatically includes it via the
 * {@code logging.pattern.console} entry in application.yml - no need to thread a
 * parameter through method signatures. Also echoed back as the {@link #RESPONSE_HEADER}
 * response header so callers can correlate their request with server-side logs.
 * MDC is thread-local and Tomcat reuses worker threads across requests, so the key is
 * always removed in a finally block to avoid leaking one request's btid into another's.
 */
public class BusinessTransactionIdFilter extends OncePerRequestFilter {
    static final String MDC_KEY = "btid";
    static final String RESPONSE_HEADER = "X-BTID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String btid = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, btid);
        response.setHeader(RESPONSE_HEADER, btid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
