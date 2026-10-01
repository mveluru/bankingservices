package org.brite.banking.exception;

/** No employee has the given employee number (mapped to 404). */
public class EmployeeNotFoundException extends RuntimeException {
    public EmployeeNotFoundException(String message) {
        super(message);
    }
}
