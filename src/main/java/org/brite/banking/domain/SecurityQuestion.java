package org.brite.banking.domain;

/** The fixed catalog of security questions a user picks three from (and answers) to be able to reset a forgotten password. */
public enum SecurityQuestion {
    FIRST_CAR("What was your first car?"),
    FIRST_SCHOOL("What was the name of your first school?"),
    FIRST_TEACHER("Who was your first teacher?"),
    FIRST_PET("What was the name of your first pet?"),
    BIRTH_CITY("In what city were you born?"),
    CHILDHOOD_FRIEND("Who was your best friend in childhood?");

    private final String text;

    SecurityQuestion(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }
}
