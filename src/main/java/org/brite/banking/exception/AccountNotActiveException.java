package org.brite.banking.exception;

/** An INACTIVE or DORMANT account can't transact (SUSPENDED and CLOSED have their own exceptions). */
public class AccountNotActiveException extends RuntimeException {
    public AccountNotActiveException(String message) {
        super(message);
    }
}
