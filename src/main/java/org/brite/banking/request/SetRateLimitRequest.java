package org.brite.banking.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.brite.banking.messages.BankingMessages;

/** A customer's own daily request limit; {@code null} (or an absent field) puts the customer back on the application default. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetRateLimitRequest {
    @Min(value = 1, message = BankingMessages.RATE_LIMIT_MIN_INVALID)
    @Max(value = 1_000_000, message = BankingMessages.RATE_LIMIT_MAX_INVALID)
    private Integer maxRequestsPerDay;
}
