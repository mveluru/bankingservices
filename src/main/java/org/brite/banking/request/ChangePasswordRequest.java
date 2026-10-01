package org.brite.banking.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.brite.banking.messages.BankingMessages;

/** A logged-in user changes their own password. Both passwords are excluded from {@code toString} and masked in request logs. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"currentPassword", "newPassword"})
public class ChangePasswordRequest {
    @NotBlank(message = BankingMessages.VALIDATION_CURRENT_PASSWORD_REQUIRED)
    private String currentPassword;

    @NotBlank(message = BankingMessages.VALIDATION_NEW_PASSWORD_REQUIRED)
    private String newPassword;
}
