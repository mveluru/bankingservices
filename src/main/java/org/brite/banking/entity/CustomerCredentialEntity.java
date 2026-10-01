package org.brite.banking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.brite.banking.domain.LoginStatus;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * Persistent form of {@link org.brite.banking.domain.CustomerCredential}; one row per customer login,
 * deliberately in its own table (not on {@code customers}). {@code customerId} is a plain id, not a
 * foreign key, like the other cross-table references to staff and locations.
 */
@Entity
@Table(name = "customer_credentials", indexes = {
        @Index(name = "idx_customer_credentials_customer_id", columnList = "customerId", unique = true),
        @Index(name = "idx_customer_credentials_username", columnList = "username", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCredentialEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 50)
    private String username;

    /** BCrypt hash (60 chars). */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private int failedAttempts;

    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;

    @Column(nullable = false)
    private LocalDateTime passwordChangedAt;

    /** Only ACTIVE may perform transactions; the default also covers rows that existed before this column. */
    @Enumerated(EnumType.STRING)
    @ColumnDefault("'ACTIVE'")
    @Column(nullable = false)
    @Builder.Default
    private LoginStatus status = LoginStatus.ACTIVE;

    @Column(length = 200)
    private String statusReason;

    private LocalDateTime statusChangedAt;
}
