package org.brite.banking.gateway;

import lombok.RequiredArgsConstructor;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.CustomerQuotaService;
import org.brite.banking.service.EmployeeQuotaService;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.JwtService;
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
 * {@link StaffAuthenticationFilter} (staff paths) and {@link CustomerAuthenticationFilter} (account and portal paths) run next
 * and demand a valid employee / customer JWT, and
 * {@link BankingRequestLoggingFilter} runs last, so it only sees requests that passed rate limiting and
 * authentication, and can be switched off with {@code banking.request-logging.enabled}.
 */
@Configuration
@RequiredArgsConstructor
public class BankingGatewayConfig {
    private static final String[] BANKING_URL_PATTERNS = {
            "/v1/api/accounts/*",
            "/v1/api/staff/*",
            "/bff/v1/staff/*",
            "/v1/api/customers/*",
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
    private final CustomerQuotaService customerQuotaService;
    private final EmployeeQuotaService employeeQuotaService;
    private final JwtService jwtService;
    private final CustomerCredentialService customerCredentialService;
    private final EmployeeCredentialService employeeCredentialService;

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
        registration.setFilter(new BankingRateLimitFilter(rateLimitProperties, customerRateLimiter, jwtService, customerQuotaService, employeeQuotaService));
        registration.setName("bankingRateLimitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        registration.addUrlPatterns(BANKING_URL_PATTERNS);
        return registration;
    }

    /** Staff endpoints (except the login) need a valid employee JWT; runs after the rate limiter. */
    @Bean
    public FilterRegistrationBean<StaffAuthenticationFilter> staffAuthenticationFilter() {
        FilterRegistrationBean<StaffAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new StaffAuthenticationFilter(jwtService, employeeCredentialService));
        registration.setName("staffAuthenticationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.addUrlPatterns("/v1/api/staff/*", "/bff/v1/staff/*");
        return registration;
    }

    /**
     * Customer-facing account and portal endpoints need a valid customer JWT (except the two that create a customer);
     * runs after the rate limiter, same slot as the staff filter (their URL patterns don't overlap).
     */
    @Bean
    public FilterRegistrationBean<CustomerAuthenticationFilter> customerAuthenticationFilter() {
        FilterRegistrationBean<CustomerAuthenticationFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CustomerAuthenticationFilter(jwtService, customerCredentialService));
        registration.setName("customerAuthenticationFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.addUrlPatterns("/v1/api/accounts/*", "/v1/api/customers/*", "/bff/v1/portal/*");
        return registration;
    }

    @Bean
    @ConditionalOnProperty(name = "banking.request-logging.enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<BankingRequestLoggingFilter> bankingRequestLoggingFilter(
            @Value("${banking.request-logging.max-payload-length:2000}") int maxPayloadLength) {
        FilterRegistrationBean<BankingRequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BankingRequestLoggingFilter(maxPayloadLength));
        registration.setName("bankingRequestLoggingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 3);
        registration.addUrlPatterns(BANKING_URL_PATTERNS);
        return registration;
    }
}
