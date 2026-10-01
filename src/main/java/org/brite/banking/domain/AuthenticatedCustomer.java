package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Who a successful customer login belongs to: just the id and name, no date of birth, phone or address. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticatedCustomer {
    private Long customerId;
    private String firstName;
    private String lastName;
}
