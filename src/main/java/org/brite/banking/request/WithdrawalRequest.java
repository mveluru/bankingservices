package org.brite.banking.request;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.brite.banking.domain.AccountType;
import org.brite.banking.messages.BankingMessages;

import java.math.BigDecimal;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawalRequest {
    String AccountNumber;

    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    Date withdrawalDate;
    BigDecimal withdrawAmount;
    String withdrawalStatus;

    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[a-zA-Z]+$",message = BankingMessages.VALIDATION_NAME_LETTERS_ONLY)
    String firstName;

    @Size(min = 1, max = 25)
    @Pattern(regexp = "^[a-zA-Z]+$",message = BankingMessages.VALIDATION_NAME_LETTERS_ONLY)
    String lastName;

    @NotBlank(message = BankingMessages.VALIDATION_STREET_REQUIRED)
    private String street;

    @NotBlank(message = BankingMessages.VALIDATION_CITY_REQUIRED)
    private String city;

    @NotBlank(message = BankingMessages.VALIDATION_STATE_REQUIRED)
    @Size(min = 2, max = 2, message = BankingMessages.VALIDATION_STATE_LENGTH)
    @Pattern(regexp = "^[A-Z]{2}$",message = BankingMessages.VALIDATION_STATE_UPPERCASE)
    private String state;

    @NotBlank(message = BankingMessages.VALIDATION_ZIP_REQUIRED)

    private String zip;

    private String addressLine1;
    private String addressLine2;


}

