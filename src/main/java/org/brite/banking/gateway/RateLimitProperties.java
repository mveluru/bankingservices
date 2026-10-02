package org.brite.banking.gateway;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Backs banking.rate-limit.* in application.yml. Enforced by {@link BankingRateLimitFilter},
 * which acts as the API-gateway ingress layer in front of every banking controller.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "banking.rate-limit")
public class RateLimitProperties {
    private boolean enabled;
    private int requestsPerDay;
    /** Default daily request limit per employee ({@code employee_rate_limits} can give one employee their own). */
    @Builder.Default
    private int employeeRequestsPerDay = 1000;
    private String customerHeaderName;
}
