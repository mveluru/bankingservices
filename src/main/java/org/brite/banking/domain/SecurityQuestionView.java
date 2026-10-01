package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A question as shown to the user (code plus text); never carries an answer. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SecurityQuestionView {
    private SecurityQuestion question;
    private String text;

    public static SecurityQuestionView of(SecurityQuestion question) {
        return new SecurityQuestionView(question, question.getText());
    }
}
