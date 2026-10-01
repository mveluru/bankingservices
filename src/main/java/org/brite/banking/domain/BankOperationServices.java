package org.brite.banking.domain;

/**
 * An operation a {@link BankLocations} can serve. Office services (everything except the
 * two {@code ATM_*} values) only apply to {@link LocationType#OFFICE}/{@link LocationType#BOTH};
 * the {@code ATM_*} values only apply to {@link LocationType#ATM}/{@link LocationType#BOTH}.
 */
public enum BankOperationServices {
    BANKING,
    SAFE_DEPOSIT_LOCKER,
    LOANS_MORTGAGES,
    NOTARY,
    WIRE_TRANSFER,
    FOREIGN_EXCHANGE,
    ATM_CASH_WITHDRAWAL,
    ATM_DEPOSIT
}
