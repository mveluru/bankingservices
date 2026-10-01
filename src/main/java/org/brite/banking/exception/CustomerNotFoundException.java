package org.brite.banking.exception;

/** No customer has the given id (mapped to 404). */
public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String message) {
        super(message);
    }
}
