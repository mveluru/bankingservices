package org.brite.banking.domain.validtors;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.time.LocalDate;

public class ValidBirthYearValidator implements ConstraintValidator<ValidBirthYear, Integer> {

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // Use @NotNull separately if needed
        }
        int currentYear = LocalDate.now().getYear();
        int maxYear = currentYear - 18;

        return value >= 1940 && value <= maxYear;
    }
}
