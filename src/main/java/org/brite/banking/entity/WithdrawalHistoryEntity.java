package org.brite.banking.entity;

import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Persistent counterpart of {@link org.brite.banking.domain.WithdrawalForm} - a separate
 * withdrawal-specific history record (distinct from {@link AccountTransactionEntity}),
 * matching the pre-existing WithdrawalRepository/TransactionRepository split. Nothing
 * currently reads this back (same as before the JPA migration - it was write-only).
 */
@Entity
@Table(name = "withdrawal_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WithdrawalHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    private LocalDate withdrawalDate;
    private BigDecimal amount;
    private String status;
    private String firstName;
    private String lastName;

    @Embedded
    private AddressEmbeddable address;
}
