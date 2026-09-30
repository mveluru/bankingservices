package org.bee.banking.bff.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the banking UI portal (a different origin) call the BFF. Preflight requests carry no
 * {@code X-Customer-Id}, so {@code BankingRateLimitFilter} lets them through and this mapping
 * answers them. The gateway/rate-limit headers are exposed so the portal can read them.
 */
@Configuration
@RequiredArgsConstructor
public class PortalCorsConfig implements WebMvcConfigurer {
    private final PortalProperties properties;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/bff/**")
                .allowedOrigins(properties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Customer-Id")
                .exposedHeaders("X-BTID", "X-RateLimit-Limit", "X-RateLimit-Remaining")
                .maxAge(3600);
    }
}
