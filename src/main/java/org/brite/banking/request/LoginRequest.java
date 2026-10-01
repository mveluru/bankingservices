package org.brite.banking.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.brite.banking.messages.BankingMessages;

/** Username and 8-digit password. The password is excluded from {@code toString} and masked by the request logger. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "password")
public class LoginRequest {
    @NotBlank(message = BankingMessages.VALIDATION_USERNAME_REQUIRED)
    private String username;

    @NotBlank(message = BankingMessages.VALIDATION_PASSWORD_REQUIRED)
    private String password;
}
