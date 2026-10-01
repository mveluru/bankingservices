package org.brite.banking.entity;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA-embeddable mirror of {@link org.brite.banking.domain.BankAddress}, inlined as columns on
 * {@link BankLocationEntity}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankAddressEmbeddable {
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String zip;
    @Builder.Default
    private String country = "USA";
}
