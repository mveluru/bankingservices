package org.brite.banking.exception;

/** Unknown username or wrong password; deliberately one message for both (mapped to 401). */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
