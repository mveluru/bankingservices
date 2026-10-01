package org.brite.banking.bff.dto;

import org.brite.banking.domain.BankOperationServices;
import org.brite.banking.domain.LocationType;

import java.time.LocalTime;
import java.util.Set;

/** Flattened branch/ATM card for the portal; {@code opensAt}/{@code closesAt}/{@code phoneNumber} are null for ATMs. */
public record PortalLocation(Long id, String name, LocationType locationType, String addressLine1, String city,
                             String state, String zip, LocalTime opensAt, LocalTime closesAt, String timeZone,
                             String phoneNumber, Set<BankOperationServices> services) {
}
