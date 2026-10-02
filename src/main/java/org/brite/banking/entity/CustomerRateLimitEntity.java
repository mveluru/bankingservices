package org.brite.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;

/**
 * Persistent form of {@link org.brite.banking.domain.CustomerRateLimit}: one row per customer, holding that customer's own daily request
 * limit (null = use {@code banking.rate-limit.requests-per-day}) and today's usage. The counters belong to {@code usageDate}; the first
 * request on a later day starts them again from zero. {@code customerId} is a plain id, not a foreign key.
 */
@Entity
@Table(name = "customer_rate_limits", indexes = {
        @Index(name = "idx_customer_rate_limits_customer_id", columnList = "customerId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRateLimitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    /** This customer's cap per day; null means the application default. */
    private Integer maxRequestsPerDay;

    @Column(nullable = false)
    private LocalDate usageDate;

    @ColumnDefault("0")
    @Column(nullable = false)
    private int requestCount;

    /** Successful customer logins on {@code usageDate}. */
    @ColumnDefault("0")
    @Column(nullable = false)
    private int loginCount;
}
