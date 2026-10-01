package org.brite.banking.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.brite.banking.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Persistent counterpart of {@link org.brite.banking.domain.AccountTransaction} - one row
 * per deposit or withdrawal, replacing TransactionRepository's old in-memory
 * Map&lt;accountNumber, List&lt;AccountTransaction&gt;&gt;.
 */
@Entity
@Table(name = "account_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountTransactionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Enumerated(EnumType.STRING)
    private TransactionType transactionType;

    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private LocalDate transactionDate;

    /** "cash" or "check"; only set for DEPOSIT transactions, null for WITHDRAWAL. */
    private String depositType;
}
