package org.brite.banking.component;

import org.brite.banking.domain.Account;
import org.brite.banking.request.AccountRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The generated MapStruct mapper must carry the registration phone number onto the customer. */
class AccountMapperPhoneTest {
    private final AccountMapper mapper = Mappers.getMapper(AccountMapper.class);

    @Test
    void registrationPhoneNumberEndsUpOnTheCustomer() {
        AccountRegistrationRequest request = AccountRegistrationRequest.builder().firstName("Jane").lastName("Doe")
                .dateOfBirth(LocalDate.of(1990, 3, 15)).phoneNumber("512-555-0101").street("111").city("Leander")
                .state("TX").zip("78717").addressLine1("Leafvillage").accountType("savings").build();

        Account account = mapper.toAccountEntity(request);

        assertThat(account.getCustomer().getPhoneNumber()).isEqualTo("512-555-0101");
        assertThat(account.getCustomer().getFirstName()).isEqualTo("Jane");
    }
}
