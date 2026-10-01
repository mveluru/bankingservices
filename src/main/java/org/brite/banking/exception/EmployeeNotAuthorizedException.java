package org.brite.banking.exception;

/** The employee is inactive or their role lacks the required privilege (mapped to 403). */
public class EmployeeNotAuthorizedException extends RuntimeException {
    public EmployeeNotAuthorizedException(String message) {
        super(message);
    }
}
