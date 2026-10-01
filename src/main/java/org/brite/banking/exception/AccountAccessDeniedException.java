package org.brite.banking.exception;

/** An authenticated customer tried to use an account that belongs to someone else (mapped to 403). */
public class AccountAccessDeniedException extends RuntimeException {
    public AccountAccessDeniedException(String message) {
        super(message);
    }
}
