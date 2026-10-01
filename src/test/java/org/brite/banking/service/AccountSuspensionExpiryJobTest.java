package org.brite.banking.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AccountSuspensionExpiryJobTest {
    @Test
    void eachRunAsksTheServiceToReactivateFinishedSuspensions() {
        AccountSuspensionService service = mock(AccountSuspensionService.class);

        new AccountSuspensionExpiryJob(service).reactivateExpiredSuspensions();

        verify(service).reactivateExpiredSuspensions();
    }
}
