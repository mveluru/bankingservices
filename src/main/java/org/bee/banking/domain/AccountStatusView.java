package org.bee.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.bee.banking.service.AccountStatusStatementService;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Flattened per-account row returned by {@link AccountStatusStatementService} - the
 * single checking-or-savings account number plus its status/lifecycle dates and the
 * owning customer's name, without the full nested {@code Account}/{@code Customer} graph.
 */
@Getter
@Builder
@AllArgsConstructor
public class AccountStatusView {
    private final String accountNumber;
    private final AccountType accountType;
    private final AccountStatus accountStatus;
    private final LocalDate createdDate;
    private final LocalDate closedDate;
    private final boolean suspended;
    private final LocalDateTime suspendedStart;
    private final LocalDateTime suspendedEnd;
    private final String suspensionNotes;
    private final String firstName;
    private final String lastName;
}
