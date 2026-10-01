package org.brite.banking.bff.dto;

import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Account detail screen: identity, balance and recent activity in one payload. While {@code suspended}
 * the account can't transact; {@code suspendedUntil} is null for an indefinite suspension.
 * {@code maskedPhoneNumber} shows only the last four digits ({@code ***-***-0101}); null if none on file.
 */
public record AccountOverviewResponse(String accountNumber, AccountType accountType, AccountStatus accountStatus,
                                      BigDecimal balance, boolean suspended, LocalDateTime suspendedUntil,
                                      LocalDate createdDate, LocalDate closedDate,
                                      String firstName, String lastName, String maskedPhoneNumber, int activityDays,
                                      List<PortalActivityItem> recentActivity) {
}
