package org.brite.banking.domain;

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.brite.banking.messages.BankingMessages;

import java.math.BigDecimal;
import java.time.LocalDate;


@AllArgsConstructor
@Data
public class WithdrawalForm {

    String AccountNumber;
    @Enumerated(EnumType.STRING)
    private AccountType accountType;

    LocalDate withdrawalDate= LocalDate.now();
    BigDecimal withdrawalAmount; // upto two decimals e.g. $100.75
    String withdrawalStatus;// Completed , Rejected

    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[a-zA-Z]+$",message = BankingMessages.VALIDATION_NAME_LETTERS_ONLY)
    String firstName;
    @Size(min = 1, max = 25)
    @Pattern(regexp = "^[a-zA-Z]+$",message = BankingMessages.VALIDATION_NAME_LETTERS_ONLY)
    String lastName;

    Address address;


}
