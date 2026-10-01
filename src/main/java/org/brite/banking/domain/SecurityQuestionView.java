package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

/** A question as shown to the user (code plus text); never carries an answer. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SecurityQuestionView {
    private SecurityQuestion question;
    private String text;

    /** Every question in the catalog, in declaration order. */
    public static List<SecurityQuestionView> catalog() {
        return Arrays.stream(SecurityQuestion.values()).map(SecurityQuestionView::of).toList();
    }

    public static SecurityQuestionView of(SecurityQuestion question) {
        return new SecurityQuestionView(question, question.getText());
    }
}
