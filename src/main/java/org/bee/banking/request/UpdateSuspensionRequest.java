package org.bee.banking.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.bee.banking.messages.BankingMessages;

import java.time.LocalDateTime;

/** Partial update of an active suspension: only the fields supplied change; at least one is required. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSuspensionRequest {
    @Size(max = 500, message = BankingMessages.VALIDATION_SUSPENSION_NOTES_MAX_LENGTH)
    private String notes;

    /** New end of the suspension; must be after the stored start and in the future. */
    private LocalDateTime endDateTime;
}
