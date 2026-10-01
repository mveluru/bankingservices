package org.brite.banking.gateway;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers the banking module's gateway filters, scoped to only its controller paths
 * (see each controller's @RequestMapping under org.brite.banking.contoller), so events
 * and the other modules endpoints are unaffected. Neither filter is a @Component itself
 * so Spring Boot doesn't also auto-register them for "/*". {@link BusinessTransactionIdFilter}
 * runs first (lower order value = higher precedence) so every request - including ones
 * {@link BankingRateLimitFilter} goes on to reject - gets a correlatable btid.
 * {@link BankingRequestLoggingFilter} runs last of the three, so it only sees requests that
 * passed rate limiting, and can be switched off with {@code banking.request-logging.enabled}.
 */
@Configuration
@RequiredArgsConstructor
public class BankingGatewayConfig {
    private static final String[] BANKING_URL_PATTERNS = {
            "/v1/api/accounts/*",
            "/v1/api/staff/*",
            "/v1/api/locations",
            "/v1/api/locations/*",
            "/v1/client/*",
            "/v1/payment/*",
            "/notify",
            "/notify-sms",
            "/report",
            "/bff/v1/portal/*"
    };

    private final RateLimitProperties rateLimitProperties;
    private final CustomerRateLimiter customerRateLimiter;

    @Bean
    public FilterRegistrationBean<BusinessTransactionIdFilter> businessTransactionIdFilter() {
        FilterRegistrationBean<BusinessTransactionIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BusinessTransactionIdFilter());
        registration.setName("businessTransactionIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns(BANKING_URL_PATTERNS);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<BankingRateLimitFilter> bankingRateLimitFilter() {
        FilterRegistrationBean<BankingRateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BankingRateLimitFilter(rateLimitProperties, customerRateLimiter));
        registration.setName("bankingRateLimitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns(BANKING_URL_PATTERNS);
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "banking.request-logging.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<BankingRequestLoggingFilter> bankingRequestLoggingFilter(
            @Value("${banking.request-logging.max-payload-length:2000}") int maxPayloadLength) {
        FilterRegistrationBean<BankingRequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BankingRequestLoggingFilter(maxPayloadLength));
        registration.setName("bankingRequestLoggingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.addUrlPatterns(BANKING_URL_PATTERNS);
        return registration;
    }
}
