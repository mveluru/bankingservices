package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response for {@code POST /v1/api/accounts/close}: best-effort bulk close, so one bad
 * account number doesn't block the others - {@code closedAccounts} holds every account
 * that was actually closed, {@code failures} explains the rest (not found / already closed).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCloseAccountsResult {
    private List<Account> closedAccounts;
    private List<BulkCloseFailure> failures;
}
