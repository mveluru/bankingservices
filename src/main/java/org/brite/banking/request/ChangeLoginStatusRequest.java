package org.brite.banking.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.messages.BankingMessages;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLoginStatusRequest {
    @NotNull(message = BankingMessages.LOGIN_STATUS_REQUIRED)
    private LoginStatus status;

    @Size(max = 200, message = BankingMessages.LOGIN_STATUS_REASON_TOO_LONG)
    private String reason;
}
