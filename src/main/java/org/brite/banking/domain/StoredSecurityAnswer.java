package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** One of a user's three security answers as stored: slot (1..3), the question and a BCrypt hash of the normalised answer. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "answerHash")
public class StoredSecurityAnswer {
    private int slot;
    private SecurityQuestion question;
    private String answerHash;
}
