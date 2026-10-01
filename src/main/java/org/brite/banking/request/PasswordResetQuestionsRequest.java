package org.brite.banking.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.brite.banking.messages.BankingMessages;

/** Asks which three security questions a user has to answer to reset their password. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetQuestionsRequest {
    @NotBlank(message = BankingMessages.VALIDATION_USERNAME_REQUIRED)
    private String username;
}
