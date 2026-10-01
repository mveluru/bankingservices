package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.Account;
import org.brite.banking.exception.AccountClosedException;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.exception.AccountSuspendedException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Suspend / update / reactivate lifecycle. A suspended account rejects every withdraw and deposit
 * (enforced in {@code AccountRepository}) until it is reactivated by hand or its {@code suspendedEnd}
 * passes ({@link AccountSuspensionExpiryJob} then reactivates it). All three mutations change fields
 * {@code AccountStatusView} shows, so each evicts the account-search cache.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountSuspensionService {
    private final AccountRepository accountRepository;

    /**
     * Suspends an ACTIVE account. {@code startDateTime} defaults to now and can't be in the future;
     * {@code endDateTime} (optional, null = indefinite) must be after the start and in the future.
     *
     * @throws AccountNotFoundException  (404) unknown account
     * @throws AccountClosedException    (400) account is closed
     * @throws AccountSuspendedException (400) account is already suspended
     * @throws IllegalArgumentException  (400) invalid start/end
     */
    @CacheEvict(cacheNames = AccountStatusStatementService.ACCOUNT_SEARCH_CACHE, allEntries = true)
    public Account suspendAccount(String accountNumber, SuspendAccountRequest request) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime start = request.getStartDateTime() != null ? request.getStartDateTime() : now;
        LocalDateTime end = request.getEndDateTime();
        if (start.isAfter(now)) {
            throw new IllegalArgumentException(String.format(BankingMessages.SUSPENSION_START_IN_FUTURE, start));
        }
        if (end != null) {
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException(String.format(BankingMessages.SUSPENSION_END_NOT_AFTER_START, end, start));
            }
            if (!end.isAfter(now)) {
                throw new IllegalArgumentException(String.format(BankingMessages.SUSPENSION_END_IN_PAST, end));
            }
        }
        return accountRepository.suspend(accountNumber, start, end, request.getNotes());
    }

    /**
     * Changes the end and/or notes of a current suspension; fields left null are unchanged.
     *
     * @throws IllegalArgumentException (400) nothing to update, end not in the future / not after the
     *                                  stored start, or the account isn't suspended
     */
    @CacheEvict(cacheNames = AccountStatusStatementService.ACCOUNT_SEARCH_CACHE, allEntries = true)
    public Account updateSuspension(String accountNumber, UpdateSuspensionRequest request) {
        if (request.getNotes() == null && request.getEndDateTime() == null) {
            throw new IllegalArgumentException(BankingMessages.SUSPENSION_UPDATE_EMPTY);
        }
        LocalDateTime end = request.getEndDateTime();
        if (end != null && !end.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException(String.format(BankingMessages.SUSPENSION_END_IN_PAST, end));
        }
        return accountRepository.updateSuspension(accountNumber, end, request.getNotes());
    }

    /** @throws IllegalArgumentException (400) if the account isn't suspended */
    @CacheEvict(cacheNames = AccountStatusStatementService.ACCOUNT_SEARCH_CACHE, allEntries = true)
    public Account reactivateAccount(String accountNumber) {
        return accountRepository.reactivate(accountNumber);
    }

    /** Reactivates suspensions whose end has passed; called by the scheduled job. Returns how many. */
    @CacheEvict(cacheNames = AccountStatusStatementService.ACCOUNT_SEARCH_CACHE, allEntries = true)
    public int reactivateExpiredSuspensions() {
        return accountRepository.reactivateExpiredSuspensions(LocalDateTime.now());
    }
}
