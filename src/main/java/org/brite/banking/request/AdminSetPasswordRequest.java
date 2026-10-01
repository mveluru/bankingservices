package org.brite.banking.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.brite.banking.messages.BankingMessages;

/** An administrator sets a login's password to a new 8-digit value. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "newPassword")
public class AdminSetPasswordRequest {
    @NotBlank(message = BankingMessages.VALIDATION_NEW_PASSWORD_REQUIRED)
    private String newPassword;
}
