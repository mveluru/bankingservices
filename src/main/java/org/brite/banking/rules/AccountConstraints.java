package org.brite.banking.rules;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "banking.constraints")
public class AccountConstraints {

    // Maximum amount allowed for a single cash deposit
    private BigDecimal maximumDepositAmountByCash;
    // Minimum age (years) required to open an account
    private int minimumAge;
    // Minimum balance a checking account must retain after a withdrawal
    private BigDecimal checkingMinimumBalance;
    // Minimum balance a savings account must retain after a withdrawal
    private BigDecimal savingMinimumBalance;
    // Maximum number of months a bank statement date range may span
    private int maxStatementRangeMonths;
}
