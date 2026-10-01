package org.brite.banking.exception;

public class BankServiceUnavailableException extends RuntimeException {
    public BankServiceUnavailableException(String message) {
        super(message);
    }
}
