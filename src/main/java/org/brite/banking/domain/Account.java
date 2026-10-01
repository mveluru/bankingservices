package org.brite.banking.domain;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"checkingAccountNumber", "savingAccountNumber"})
public class Account implements Serializable {
    String checkingAccountNumber;
    String savingAccountNumber;
    BigDecimal checkingBalance;
    BigDecimal savingBalance;
    @Enumerated(EnumType.STRING)
    private AccountType accountType;
    @Enumerated(EnumType.STRING)
    private AccountStatus accountStatus;
    private LocalDate createdDate;
    private LocalDate closedDate;
    /** True exactly while {@code accountStatus == SUSPENDED}. */
    private boolean suspended;
    private LocalDateTime suspendedStart;
    /** Null = indefinite suspension. */
    private LocalDateTime suspendedEnd;
    private String suspensionNotes;
    Customer customer;
}
