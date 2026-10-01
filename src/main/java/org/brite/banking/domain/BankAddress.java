package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Street address of a {@link BankLocations}. Same shape as the customer {@link Address},
 * minus the redundant {@code street} field.
 */
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankAddress implements Serializable {
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zip;
    @Builder.Default
    private String country = "USA";
}
