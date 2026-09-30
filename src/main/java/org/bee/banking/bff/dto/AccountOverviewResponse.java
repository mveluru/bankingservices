package org.bee.banking.bff.dto;

import org.bee.banking.domain.AccountStatus;
import org.bee.banking.domain.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Account detail screen: identity, balance and recent activity in one payload. While {@code suspended}
 * the account can't transact; {@code suspendedUntil} is null for an indefinite suspension.
 */
public record AccountOverviewResponse(String accountNumber, AccountType accountType, AccountStatus accountStatus,
                                      BigDecimal balance, boolean suspended, LocalDateTime suspendedUntil,
                                      LocalDate createdDate, LocalDate closedDate,
                                      String firstName, String lastName, int activityDays,
                                      List<PortalActivityItem> recentActivity) {
}
