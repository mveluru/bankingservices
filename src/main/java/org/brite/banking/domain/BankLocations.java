package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalTime;
import java.util.Set;

/**
 * A bank branch office and/or ATM. {@code opensAt}/{@code closesAt}/{@code phoneNumber} are
 * the <em>office's</em> hours and phone, so they are {@code null} for {@link LocationType#ATM}
 * (ATMs are available around the clock and have no front desk). Hours are wall-clock times in
 * {@code timeZone} (an IANA id, "America/Chicago" for Central time - unlike the literal "CST"
 * it follows daylight saving, so 8-4 stays 8-4 year-round).
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankLocations implements Serializable {
    private Long id;
    private String name;
    private BankAddress bankAddress;
    private LocationType locationType;
    private LocalTime opensAt;
    private LocalTime closesAt;
    @Builder.Default
    private String timeZone = "America/Chicago";
    private String phoneNumber;
    private Set<BankOperationServices> services;
}
