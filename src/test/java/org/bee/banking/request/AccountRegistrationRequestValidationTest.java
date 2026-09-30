package org.bee.banking.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Plain Bean Validation (no Spring context): the phone number rules on registration. */
class AccountRegistrationRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private AccountRegistrationRequest.AccountRegistrationRequestBuilder valid() {
        return AccountRegistrationRequest.builder().firstName("Jane").lastName("Doe").dateOfBirth(LocalDate.of(1990, 3, 15))
                .phoneNumber("512-555-0101").street("111").city("Leander").state("TX").zip("78717")
                .addressLine1("Leafvillage").accountType("checking");
    }

    private Set<ConstraintViolation<AccountRegistrationRequest>> violationsOn(AccountRegistrationRequest request, String property) {
        return validator.validateProperty(request, property);
    }

    @Test
    void aWellFormedRequestHasNoViolations() {
        assertThat(validator.validate(valid().build())).isEmpty();
    }

    @Test
    void missingPhoneNumberIsRejected() {
        for (String blank : new String[]{null, "", "   "}) {
            assertThat(violationsOn(valid().phoneNumber(blank).build(), "phoneNumber"))
                    .extracting(ConstraintViolation::getMessage).contains("Phone number is required");
        }
    }

    @Test
    void onlyTheThreeThreeFourHyphenatedFormatIsAccepted() {
        for (String bad : new String[]{"5125550101", "(512) 555-0101", "512.555.0101", "512-555-010", "512-555-01011", "+1-512-555-0101", "abc-def-ghij"}) {
            assertThat(violationsOn(valid().phoneNumber(bad).build(), "phoneNumber"))
                    .as(bad).extracting(ConstraintViolation::getMessage)
                    .contains("Phone number must be in the format 512-555-0101");
        }
        assertThat(violationsOn(valid().phoneNumber("713-555-0142").build(), "phoneNumber")).isEmpty();
    }
}
