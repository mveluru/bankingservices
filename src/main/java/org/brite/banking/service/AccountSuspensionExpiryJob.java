package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically returns suspended accounts to ACTIVE once their {@code suspendedEnd} has passed.
 * Runs through {@link AccountSuspensionService} (a separate bean) so its {@code @CacheEvict} proxy applies.
 * Until a run happens an expired suspension still blocks transactions, so the lag is at most one interval.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "banking.suspension.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class AccountSuspensionExpiryJob {
    private final AccountSuspensionService suspensionService;

    @Scheduled(fixedDelayString = "${banking.suspension.expiry-job.interval-ms:60000}",
            initialDelayString = "${banking.suspension.expiry-job.interval-ms:60000}")
    public void reactivateExpiredSuspensions() {
        suspensionService.reactivateExpiredSuspensions();
    }
}
