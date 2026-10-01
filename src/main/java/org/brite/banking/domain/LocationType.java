package org.brite.banking.domain;

/**
 * What a {@link BankLocations} physically is: a staffed branch office, a stand-alone ATM,
 * or a branch that also has an ATM on site.
 */
public enum LocationType {
    OFFICE,
    ATM,
    BOTH
}
