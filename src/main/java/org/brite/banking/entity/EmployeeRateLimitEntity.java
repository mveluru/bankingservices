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
 * Persistent form of {@link org.brite.banking.domain.EmployeeRateLimit}: one row per employee, holding that employee's own daily request limit
 * (null = use {@code banking.rate-limit.employee-requests-per-day}) and today's usage; the staff counterpart of {@link CustomerRateLimitEntity}.
 * The counters belong to {@code usageDate}; the first request on a later day starts them again from zero. {@code employeeNumber} is a plain
 * value, not a foreign key.
 */
@Entity
@Table(name = "employee_rate_limits", indexes = {
        @Index(name = "idx_employee_rate_limits_employee_number", columnList = "employeeNumber", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeRateLimitEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String employeeNumber;

    /** This employee's cap per day; null means the application default. */
    private Integer maxRequestsPerDay;

    @Column(nullable = false)
    private LocalDate usageDate;

    @ColumnDefault("0")
    @Column(nullable = false)
    private int requestCount;

    /** Successful employee logins on {@code usageDate}. */
    @ColumnDefault("0")
    @Column(nullable = false)
    private int loginCount;
}
