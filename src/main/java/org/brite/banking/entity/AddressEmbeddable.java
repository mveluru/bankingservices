package org.brite.banking.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA-embeddable mirror of {@link org.brite.banking.domain.Address}, inlined as columns
 * on whichever entity owns it (customer, withdrawal history) rather than its own table.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AddressEmbeddable {
    private String street;
    private String city;
    private String state;
    private String zip;
    @Builder.Default
    private String country = "USA";
    private String addressLine1;
    private String addressLine2;
}
