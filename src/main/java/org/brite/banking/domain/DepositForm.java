package org.brite.banking.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.brite.banking.messages.BankingMessages;

import java.math.BigDecimal;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DepositForm {
    String AccountNumber;
    BigDecimal amount;
    AccountType accountType;
    @NotBlank(message = BankingMessages.VALIDATION_DEPOSIT_TYPE_REQUIRED)
    String  depositType; // cash or check

    @Size(min = 1, max = 50)
    @Pattern(regexp = "[a-zA-Z]+$",message = BankingMessages.VALIDATION_NAME_LETTERS_ONLY)
    String firstName;

    @Size(min = 1, max = 25)
    @Pattern(regexp = "[a-zA-Z]+$",message = BankingMessages.VALIDATION_NAME_LETTERS_ONLY)
    String lastName;

    @NotBlank(message = BankingMessages.VALIDATION_STREET_REQUIRED)
    private String street;

    @NotBlank(message = BankingMessages.VALIDATION_ADDRESS_LINE1_REQUIRED)
    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[a-zA-z0-9 .-_]+$",message = BankingMessages.VALIDATION_DEPOSIT_ADDRESS_LINE1_INVALID_CHARS)
    private String addressLine1;
    @Pattern(regexp = "^[a-zA-Z0-9 ._-]+$")
    @Size(min = 0, max = 50)
    private String addressLine2;

    @NotBlank(message = BankingMessages.VALIDATION_CITY_REQUIRED)
    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[a-zA-z0-9 .-]+$",message = BankingMessages.VALIDATION_CITY_INVALID_CHARS)
    private String city;

    @NotBlank(message = BankingMessages.VALIDATION_STATE_REQUIRED)
    @Size(min = 2, max = 2, message = BankingMessages.VALIDATION_STATE_LENGTH)
    @Pattern(regexp = "^[A-Z]{2}$",message = BankingMessages.VALIDATION_STATE_UPPERCASE)
    private String state;

    /*
    In Bean Validation's @Pattern, the regex engine evaluates the expression using Matcher.matches().
    This implicitly matches the entire string. However, explicitly adding ^ (start of string) and $ (end of string)
    is a best practice in validation frameworks to guarantee no leading or trailing characters sneak past
     */
    @NotBlank(message = BankingMessages.VALIDATION_ZIP_REQUIRED)
    @Pattern(regexp = "^\\d{5}$",message = BankingMessages.VALIDATION_ZIP_FORMAT)
    private String zip;


}
