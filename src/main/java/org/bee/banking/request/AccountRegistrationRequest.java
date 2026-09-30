package org.bee.banking.request;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bee.banking.domain.validtors.ValidBirthYear;
import org.bee.banking.messages.BankingMessages;
import org.hibernate.validator.constraints.Range;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountRegistrationRequest {
    @NotBlank(message = BankingMessages.VALIDATION_FIRST_NAME_REQUIRED)
    @Size(max = 50, message = BankingMessages.VALIDATION_FIRST_NAME_MAX_LENGTH)
    private String firstName;

    @NotBlank(message = BankingMessages.VALIDATION_LAST_NAME_REQUIRED)
    @Size(max = 50, message = BankingMessages.VALIDATION_LAST_NAME_MAX_LENGTH)
    private String lastName;

    @NotBlank(message = BankingMessages.VALIDATION_PHONE_REQUIRED)
    @Pattern(regexp = "^\\d{3}-\\d{3}-\\d{4}$", message = BankingMessages.VALIDATION_PHONE_FORMAT)
    private String phoneNumber;

    @NotNull(message = BankingMessages.VALIDATION_DOB_REQUIRED)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy")
    private LocalDate dateOfBirth;

    @NotBlank(message = BankingMessages.VALIDATION_STREET_REQUIRED)
    private String street;

    @NotBlank(message = BankingMessages.VALIDATION_CITY_REQUIRED)
    private String city;

    @NotBlank(message = BankingMessages.VALIDATION_STATE_REQUIRED)
    @Size(min = 2, max = 2, message = BankingMessages.VALIDATION_STATE_LENGTH)
    @Pattern(regexp = "^[A-Z]{2}$",message = BankingMessages.VALIDATION_STATE_UPPERCASE)
    private String state;

    @NotBlank(message = BankingMessages.VALIDATION_ZIP_REQUIRED)
    @Pattern(regexp = "^\\d{5}$",message = BankingMessages.VALIDATION_ZIP_FORMAT)
    private String zip;

    @NotBlank(message = BankingMessages.VALIDATION_ADDRESS_LINE1_REQUIRED)
    @Size(min = 1, max = 50, message = BankingMessages.VALIDATION_ADDRESS_LINE1_REQUIRED)
    private String addressLine1;

    private String addressLine2;

    @NotBlank(message = BankingMessages.VALIDATION_ACCOUNT_TYPE_REQUIRED)
    private String accountType; // Accepts raw text string like "checking" or "SAVINGS"

    // Custom helper method to enforce the 1940 cutoff rule automatically
    @Range(min = 1940, max = 3000, message = BankingMessages.VALIDATION_DOB_YEAR_MIN)
    @ValidBirthYear(message = BankingMessages.VALIDATION_DOB_YEAR_MIN)
    public int getDateOfBirthYear() {
        return dateOfBirth != null ? dateOfBirth.getYear() : 1940;
    }
}


/*
1. The +$ Variant (One or more characters until the end)In your original pattern ^[A-Z]+$:+ is a quantifier meaning "match 1 or more times".$ means "assert position at the end of the string".Combined, ^[A-Z]+$ means: "The string must start with uppercase letters, contain at least one uppercase letter, and continue matching uppercase letters all the way to the end of the string." It prevents trailing invalid characters (e.g., "TX1" or "TX ").2. The $ Variant (Exact counts or single matches)When the + is missing, it is usually because a specific quantifier or a single character check is right before the $.Exact Quantifier: ^[A-Z]{2}$The {2} replaces the +. It means "match exactly 2 times". The $ immediately follows to ensure nothing else comes after those 2 characters.Single Character: ^[A-Z]$Without any quantifier, it matches exactly one uppercase letter from start to finish.
 */