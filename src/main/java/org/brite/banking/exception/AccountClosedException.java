package org.brite.banking.exception;

public class AccountClosedException extends RuntimeException {
    public AccountClosedException() {
    }
    public AccountClosedException(String message) {
        super(message);
    }
}
