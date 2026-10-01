package org.brite.banking.exception;

/** Login refused because of too many failed attempts (mapped to 423). */
public class EmployeeLockedException extends RuntimeException {
    public EmployeeLockedException(String message) {
        super(message);
    }
}
