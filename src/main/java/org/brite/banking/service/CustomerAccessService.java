package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.exception.AccountAccessDeniedException;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.AccountRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

/**
 * Keeps an authenticated customer on their own accounts. The customer id comes from the verified customer token
 * ({@link org.brite.banking.gateway.CustomerAuthenticationFilter}); every customer-facing handler passes it here
 * before touching an account.
 * <p>
 * An account that doesn't exist is left to the operation itself to report (404); only an account that exists and
 * belongs to someone else is refused (403).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerAccessService {
    private final AccountRepository accountRepository;

    /**
     * Fails closed: a request that reaches a customer handler without an authenticated customer (the filter was
     * bypassed or misconfigured) is rejected rather than treated as "all customers".
     *
     * @throws InvalidTokenException (mapped to 401) if there is no authenticated customer
     */
    public Long requireAuthenticated(Long customerId) {
        if (customerId == null) {
            throw new InvalidTokenException(BankingMessages.CUSTOMER_AUTHENTICATION_REQUIRED);
        }
        return customerId;
    }

    /**
     * @throws InvalidTokenException (mapped to 401) if there is no authenticated customer
     * @throws AccountAccessDeniedException (mapped to 403) if the account belongs to another customer
     */
    public void requireOwnAccount(Long customerId, String accountNumber) {
        requireAuthenticated(customerId);
        Optional<Long> owner = accountRepository.findCustomerIdByAccountNumber(accountNumber);
        if (owner.isPresent() && !owner.get().equals(customerId)) {
            log.warn(BankingMessages.LOG_ACCOUNT_ACCESS_DENIED, customerId, accountNumber);
            throw new AccountAccessDeniedException(String.format(BankingMessages.ACCOUNT_NOT_OWNED, accountNumber));
        }
    }

    /** All-or-nothing: if any account isn't theirs the whole request is refused before anything happens. */
    public void requireOwnAccounts(Long customerId, Collection<String> accountNumbers) {
        requireAuthenticated(customerId);
        if (accountNumbers != null) {
            accountNumbers.forEach(accountNumber -> requireOwnAccount(customerId, accountNumber));
        }
    }
}
