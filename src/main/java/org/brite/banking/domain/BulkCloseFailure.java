package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One account that couldn't be closed as part of a {@code POST /v1/api/accounts/close}
 * bulk request - e.g. it doesn't exist or is already closed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCloseFailure {
    private String accountNumber;
    private String reason;
}
