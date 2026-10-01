package org.brite.banking.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.brite.banking.messages.BankingMessages;

import java.util.List;

/** Resets a forgotten password: the username, the answers to the user's three security questions and the new 8-digit password. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "newPassword")
public class PasswordResetRequest {
    @NotBlank(message = BankingMessages.VALIDATION_USERNAME_REQUIRED)
    private String username;

    @NotNull(message = BankingMessages.SECURITY_ANSWERS_COUNT)
    @Size(min = 3, max = 3, message = BankingMessages.SECURITY_ANSWERS_COUNT)
    @Valid
    private List<SecurityAnswerRequest> answers;

    @NotBlank(message = BankingMessages.VALIDATION_NEW_PASSWORD_REQUIRED)
    private String newPassword;
}
