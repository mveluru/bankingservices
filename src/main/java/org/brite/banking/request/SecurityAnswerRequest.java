package org.brite.banking.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.messages.BankingMessages;

/** One question (from the fixed catalog) and the user's answer. The answer is excluded from {@code toString} and masked in request logs. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "answer")
public class SecurityAnswerRequest {
    @NotNull(message = BankingMessages.VALIDATION_QUESTION_REQUIRED)
    private SecurityQuestion question;

    @NotBlank(message = BankingMessages.VALIDATION_ANSWER_REQUIRED)
    private String answer;
}
