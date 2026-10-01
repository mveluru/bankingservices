package org.brite.banking.domain;

import lombok.Getter;

@Getter
public enum AccountType {
    CHECKING("Checking Account"),
    SAVINGS("Savings Account"),
    INVESTMENT("Brokerage Investment"),
    RETIREMENT("Retirement Portfolio"),
    CREDIT_OR_LOAN("Line of Credit / Loan");

    // Getter
    private final String displayName;

    // Enum Constructor
    AccountType(String displayName) {
        this.displayName = displayName;
    }

}
