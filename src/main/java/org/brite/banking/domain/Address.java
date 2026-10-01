package org.brite.banking.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.brite.banking.messages.BankingMessages;

import java.io.Serializable;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Address implements Serializable {
    @NotBlank
    private String street;
    @NotBlank(message = BankingMessages.VALIDATION_CITY_REQUIRED)
    @Size(min = 1, max = 50)
    @Pattern(regexp = "^[a-zA-z0-9 .-]+$",message = BankingMessages.VALIDATION_CITY_INVALID_CHARS)
    private String city;
    @NotBlank
    @NotBlank(message = BankingMessages.VALIDATION_STATE_REQUIRED)
    @Size(min = 2, max = 2, message = BankingMessages.VALIDATION_STATE_LENGTH)
    @Pattern(regexp = "^[A-Z]{2}$",message = BankingMessages.VALIDATION_STATE_UPPERCASE)
    private String state;

    @Pattern(regexp = "^\\d{5}$",message = BankingMessages.VALIDATION_ZIP_FORMAT)
    @NotBlank(message = BankingMessages.VALIDATION_ZIP_REQUIRED)
    private String zip;
    @Builder.Default
    private String country="USA";
    @NotBlank
    @Size(min = 1, max = 50)
    private String addressLine1;
    @Size(min = 0, max = 50)
    private String addressLine2;


}
