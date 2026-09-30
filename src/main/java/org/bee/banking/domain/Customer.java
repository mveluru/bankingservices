package org.bee.banking.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.bee.banking.messages.BankingMessages;
import org.springframework.lang.NonNull;

import java.io.Serializable;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer implements Serializable {
    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[a-zA-Z]+$",message = BankingMessages.VALIDATION_NAME_LETTERS_ONLY)
    String firstName;
    @Size(min = 1, max = 25)
    @Pattern(regexp = "^[a-zA-Z]+$",message = BankingMessages.VALIDATION_NAME_LETTERS_ONLY)
    String lastName;
    Address address;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "MM/dd/yyyy")
    @NonNull
    private LocalDate dateOfBirth;

    /** US format {@code 512-555-0101}; null for customers registered before phone numbers existed. */
    private String phoneNumber;


    // The method name must start with "is" for the validator to pick it up automatically
    @AssertTrue(message = BankingMessages.VALIDATION_DOB_YEAR_1940)
    public boolean isDateOfBirthValid() {
        return dateOfBirth != null && dateOfBirth.getYear() >= 1940;
    }

    public Customer(@NonNull  String firstName, @NonNull String lastName, Address address) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
    }


}
