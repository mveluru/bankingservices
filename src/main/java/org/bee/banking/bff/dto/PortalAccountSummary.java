package org.bee.banking.bff.dto;

import org.bee.banking.domain.AccountStatus;
import org.bee.banking.domain.AccountType;

import java.time.LocalDate;

/** One account row on the portal home screen. Deliberately has no balance (source view is cached). */
public record PortalAccountSummary(String accountNumber, AccountType accountType, AccountStatus accountStatus,
                                   LocalDate createdDate, LocalDate closedDate, String firstName, String lastName) {
}
