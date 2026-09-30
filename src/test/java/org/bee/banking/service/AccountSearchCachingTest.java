package org.bee.banking.service;

import org.bee.banking.domain.AccountStatus;
import org.bee.banking.request.AccountRegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Method;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit test (no Spring context) guarding the declarative caching wiring:
 * AccountStatusStatementService.listAccountStatuses is @Cacheable, and the two
 * ClientAccountService mutations that change a field AccountStatusView exposes
 * (register, close) evict that same cache. Actual cache behavior (population, TTL
 * expiry) is a Spring-proxy concern that needs a running ApplicationContext to
 * observe, so this only verifies the annotations stay in place and stay consistent.
 */
class AccountSearchCachingTest {

    @Test
    void listAccountStatuses_isCacheableUnderTheSharedAccountSearchCache() throws NoSuchMethodException {
        Method method = AccountStatusStatementService.class.getMethod("listAccountStatuses",
                String.class, AccountStatus.class, LocalDate.class, LocalDate.class,
                LocalDate.class, LocalDate.class, Integer.class, Pageable.class);

        Cacheable cacheable = method.getAnnotation(Cacheable.class);

        assertThat(cacheable).isNotNull();
        assertThat(cacheable.cacheNames()).containsExactly(AccountStatusStatementService.ACCOUNT_SEARCH_CACHE);
    }

    @Test
    void registerNewClientAccount_evictsTheAccountSearchCache() throws NoSuchMethodException {
        Method method = ClientAccountService.class.getMethod("registerNewClientAccount", AccountRegistrationRequest.class);

        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

        assertThat(cacheEvict).isNotNull();
        assertThat(cacheEvict.cacheNames()).containsExactly(AccountStatusStatementService.ACCOUNT_SEARCH_CACHE);
        assertThat(cacheEvict.allEntries()).isTrue();
    }

    @Test
    void closeAccount_evictsTheAccountSearchCache() throws NoSuchMethodException {
        Method method = ClientAccountService.class.getMethod("closeAccount", String.class);

        CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);

        assertThat(cacheEvict).isNotNull();
        assertThat(cacheEvict.cacheNames()).containsExactly(AccountStatusStatementService.ACCOUNT_SEARCH_CACHE);
        assertThat(cacheEvict.allEntries()).isTrue();
    }

    @Test
    void everySuspensionMutationEvictsTheAccountSearchCache() throws NoSuchMethodException {
        Method[] mutations = {
                AccountSuspensionService.class.getMethod("suspendAccount", String.class, org.bee.banking.request.SuspendAccountRequest.class),
                AccountSuspensionService.class.getMethod("updateSuspension", String.class, org.bee.banking.request.UpdateSuspensionRequest.class),
                AccountSuspensionService.class.getMethod("reactivateAccount", String.class),
                AccountSuspensionService.class.getMethod("reactivateExpiredSuspensions")
        };

        for (Method method : mutations) {
            CacheEvict cacheEvict = method.getAnnotation(CacheEvict.class);
            assertThat(cacheEvict).as(method.getName()).isNotNull();
            assertThat(cacheEvict.cacheNames()).containsExactly(AccountStatusStatementService.ACCOUNT_SEARCH_CACHE);
            assertThat(cacheEvict.allEntries()).isTrue();
        }
    }
}
