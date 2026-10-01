package org.brite.banking.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.brite.banking.messages.BankingMessages;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkCloseAccountsRequest {
    @NotEmpty(message = BankingMessages.VALIDATION_ACCOUNT_NUMBERS_REQUIRED)
    private List<String> accountNumbers;
}
