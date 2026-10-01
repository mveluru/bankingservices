package org.brite.banking.domain.validtors;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidBirthYearValidator.class)
@Documented
public @interface ValidBirthYear {
    String message() default "Year must be between 1940 and 18 years ago";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
