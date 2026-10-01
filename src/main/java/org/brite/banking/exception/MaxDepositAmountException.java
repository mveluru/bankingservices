package org.brite.banking.exception;

public class MaxDepositAmountException extends RuntimeException {
    public MaxDepositAmountException(String message) {
        super(message);
    }
}
