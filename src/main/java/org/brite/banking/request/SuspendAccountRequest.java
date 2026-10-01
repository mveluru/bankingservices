package org.brite.banking.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.brite.banking.messages.BankingMessages;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspendAccountRequest {
    @NotBlank(message = BankingMessages.VALIDATION_SUSPENSION_NOTES_REQUIRED)
    @Size(max = 500, message = BankingMessages.VALIDATION_SUSPENSION_NOTES_MAX_LENGTH)
    private String notes;

    /** ISO local date-time, e.g. 2026-10-01T09:30:00. Omitted = now; must not be in the future. */
    private LocalDateTime startDateTime;

    /** ISO local date-time. Omitted = indefinite suspension; must be after the start and in the future. */
    private LocalDateTime endDateTime;
}
