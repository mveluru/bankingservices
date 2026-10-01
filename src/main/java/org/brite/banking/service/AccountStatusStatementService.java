package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountStatusView;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.AccountRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * Pulls accounts together with their status/lifecycle dates and owning customer's
 * name, paginated. Owns the account-search validation and the Account -&gt;
 * AccountStatusView mapping; AccountRepository.search() stays purely a data-access
 * concern (filter/sort/paginate the in-memory store), with no knowledge of this view.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountStatusStatementService {
    /** Default lookback window applied when neither createdFrom nor createdTo is given. */
    public static final int DEFAULT_LOOKBACK_MONTHS = 18;

    /** Shared with ClientAccountService's @CacheEvict on register/close - see application.yml for TTL (10m). */
    public static final String ACCOUNT_SEARCH_CACHE = "accountSearch";

    private final AccountRepository accountRepository;

    /**
     * Retrieves account ids/details within a createdDate/closedDate range. If neither
     * {@code createdFrom} nor {@code createdTo} is given, defaults to a trailing window
     * of {@code months} months ending today (falling back to {@link #DEFAULT_LOOKBACK_MONTHS}
     * if {@code months} is also omitted) - i.e. "as of today minus N months". Supplying
     * either explicit created-date bound disables this default entirely; {@code months}
     * is then ignored. Conditional lookup: when {@code accountNumber} is provided, the
     * result is narrowed to just that account (still subject to the resolved date-range/
     * status filters, so an out-of-range account yields an empty page rather than
     * bypassing the range); when it's omitted/null, every matching account is returned.
     * <p>
     * Cached for 10 minutes (see {@code spring.cache.caffeine.spec} in application.yml)
     * since {@link AccountStatusView} never exposes balance (and {@code customerId}, being a method argument, is part of the
     * cache key, so one customer's cached page is never served to another) - it's only invalidated by
     * {@code ClientAccountService.registerNewClientAccount}/{@code closeAccount} and the
     * {@code AccountSuspensionService} mutations, the only operations that change a field
     * this view actually shows.
     */
    @Cacheable(cacheNames = ACCOUNT_SEARCH_CACHE)
    public Page<AccountStatusView> listAccountStatuses(String accountNumber, AccountStatus status,
                                                       LocalDate createdFrom, LocalDate createdTo,
                                                       LocalDate closedFrom, LocalDate closedTo,
                                                       Integer months, Long customerId, Pageable pageable) {
        if (months != null && months <= 0) {
            log.warn(BankingMessages.LOG_ACCOUNT_SEARCH_REJECTED_MONTHS, months);
            throw new IllegalArgumentException(String.format(BankingMessages.MONTHS_MUST_BE_POSITIVE, months));
        }

        LocalDate effectiveCreatedFrom = createdFrom;
        LocalDate effectiveCreatedTo = createdTo;
        if (effectiveCreatedFrom == null && effectiveCreatedTo == null) {
            int lookbackMonths = months != null ? months : DEFAULT_LOOKBACK_MONTHS;
            effectiveCreatedTo = LocalDate.now();
            effectiveCreatedFrom = effectiveCreatedTo.minusMonths(lookbackMonths);
            log.debug(BankingMessages.LOG_ACCOUNT_SEARCH_DEFAULT_LOOKBACK_APPLIED, lookbackMonths, effectiveCreatedFrom, effectiveCreatedTo);
        }

        if (effectiveCreatedFrom != null && effectiveCreatedTo != null && effectiveCreatedFrom.isAfter(effectiveCreatedTo)) {
            String message = String.format(BankingMessages.CREATED_DATE_RANGE_INVALID, effectiveCreatedFrom, effectiveCreatedTo);
            log.warn(BankingMessages.LOG_ACCOUNT_SEARCH_REJECTED_DATE_RANGE, message);
            throw new IllegalArgumentException(message);
        }
        if (closedFrom != null && closedTo != null && closedFrom.isAfter(closedTo)) {
            String message = String.format(BankingMessages.CLOSED_DATE_RANGE_INVALID, closedFrom, closedTo);
            log.warn(BankingMessages.LOG_ACCOUNT_SEARCH_REJECTED_DATE_RANGE, message);
            throw new IllegalArgumentException(message);
        }

        log.debug(BankingMessages.LOG_ACCOUNT_SEARCH, accountNumber, status, effectiveCreatedFrom, effectiveCreatedTo, closedFrom, closedTo, pageable.getPageNumber());
        Page<Account> accounts = accountRepository.search(accountNumber, status, effectiveCreatedFrom, effectiveCreatedTo, closedFrom, closedTo, customerId, pageable);
        return accounts.map(this::toView);
    }

    private AccountStatusView toView(Account account) {
        String accountNumber = account.getCheckingAccountNumber() != null
                ? account.getCheckingAccountNumber() : account.getSavingAccountNumber();
        return AccountStatusView.builder()
                .accountNumber(accountNumber)
                .accountType(account.getAccountType())
                .accountStatus(account.getAccountStatus())
                .createdDate(account.getCreatedDate())
                .closedDate(account.getClosedDate())
                .suspended(account.isSuspended())
                .suspendedStart(account.getSuspendedStart())
                .suspendedEnd(account.getSuspendedEnd())
                .suspensionNotes(account.getSuspensionNotes())
                .firstName(account.getCustomer() != null ? account.getCustomer().getFirstName() : null)
                .lastName(account.getCustomer() != null ? account.getCustomer().getLastName() : null)
                .build();
    }
}
