package org.brite.banking.exception;

/** A customer can't sign in because none of their accounts is ACTIVE; the message names the status and says to contact customer support. */
public class AccountHolderLoginBlockedException extends RuntimeException {
    public AccountHolderLoginBlockedException(String message) {
        super(message);
    }
}
