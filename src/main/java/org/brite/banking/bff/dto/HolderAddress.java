package org.brite.banking.bff.dto;

import org.brite.banking.domain.Address;

/**
 * The account holder's address on file, so the portal can pre-fill the holder fields of a withdraw or deposit and a teller can confirm it with
 * the customer. {@code addressLine2} and {@code country} may be null. Unlike the phone number it is not masked: it is only ever returned to
 * the owning customer's own token or to an employee with VIEW_ACCOUNT, and it is never logged.
 */
public record HolderAddress(String street, String addressLine1, String addressLine2, String city, String state, String zip, String country) {

    /** Null when the customer has no address on file. */
    public static HolderAddress of(Address a) {
        return a == null ? null : new HolderAddress(a.getStreet(), a.getAddressLine1(), a.getAddressLine2(), a.getCity(), a.getState(), a.getZip(), a.getCountry());
    }
}
