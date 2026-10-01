package org.brite.banking.bff.dto;

import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One account row on the portal home screen. Deliberately has no balance (source view is cached).
 * {@code suspendedUntil} is null for an indefinite suspension; suspension notes are not exposed.
 */
public record PortalAccountSummary(String accountNumber, AccountType accountType, AccountStatus accountStatus,
                                   boolean suspended, LocalDateTime suspendedUntil,
                                   LocalDate createdDate, LocalDate closedDate, String firstName, String lastName) {
}
