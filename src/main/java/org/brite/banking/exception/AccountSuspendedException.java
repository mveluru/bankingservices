package org.brite.banking.exception;

/** An account is suspended: it can't transact, and can't be suspended again until reactivated. */
public class AccountSuspendedException extends RuntimeException {
    public AccountSuspendedException(String message) {
        super(message);
    }
}
