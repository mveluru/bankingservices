package org.brite.banking.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.brite.banking.messages.BankingMessages;

@Data
public class AccountLookupRequest {
    @NotBlank(message = BankingMessages.VALIDATION_ACCOUNT_NUMBER_REQUIRED)
    private String accountNumber;
}
