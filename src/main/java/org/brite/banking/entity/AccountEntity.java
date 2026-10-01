package org.brite.banking.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per real account (checking OR savings) - unlike the in-memory-era
 * {@link org.brite.banking.domain.Account} domain class, which conflated a checking and a
 * savings account into one object with a pair of nullable fields per type. That shape
 * only made sense for the old dbMockStore keyed by account number; a relational table
 * models "one account number = one row" directly. AccountRepository maps between the
 * two shapes so the service layer keeps using the existing Account domain object.
 */
@Entity
@Table(name = "accounts", indexes = @Index(name = "idx_accounts_account_number", columnList = "accountNumber", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;

    private BigDecimal balance;

    private LocalDate createdDate;
    private LocalDate closedDate;

    /** True exactly while {@code accountStatus == SUSPENDED}; kept as its own column for cheap filtering/reporting. */
    @Column(nullable = false)
    @Builder.Default
    private boolean suspended = false;

    /** When the current suspension began; null unless suspended. */
    private LocalDateTime suspendedStart;

    /** When the suspension lifts automatically; null = indefinite (until reactivated by hand). */
    private LocalDateTime suspendedEnd;

    /** Why the account was suspended (and any later updates); null unless suspended. */
    @Column(length = 500)
    private String suspensionNotes;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private CustomerEntity customer;

    /** Optimistic locking - protects concurrent withdraw/deposit from lost updates now that balance changes go through the DB instead of an atomic ConcurrentHashMap.computeIfPresent. */
    @Version
    private Long version;
}
