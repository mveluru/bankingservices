package org.brite.banking.exception;

/** The token is malformed, has a bad signature or issuer, is of the wrong kind, or has expired (mapped to 401). */
public class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String message) {
        super(message);
    }
}
