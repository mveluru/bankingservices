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

/** Choose and answer three security questions; needs the current password so a stolen token alone can't change them. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "currentPassword")
public class SetSecurityQuestionsRequest {
    @NotBlank(message = BankingMessages.VALIDATION_CURRENT_PASSWORD_REQUIRED)
    private String currentPassword;

    @NotNull(message = BankingMessages.SECURITY_ANSWERS_COUNT)
    @Size(min = 3, max = 3, message = BankingMessages.SECURITY_ANSWERS_COUNT)
    @Valid
    private List<SecurityAnswerRequest> answers;
}
