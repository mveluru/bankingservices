package org.brite.banking.exception;

/** The login is INACTIVE, SUSPENDED or LOCKED (or an employee has none), so it may not transact (mapped to 403). */
public class LoginNotActiveException extends RuntimeException {
    public LoginNotActiveException(String message) {
        super(message);
    }
}
