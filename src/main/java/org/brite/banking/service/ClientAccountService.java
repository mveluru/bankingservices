package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.component.AccountMapper;
import org.brite.banking.component.WithdrawalMapper;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountTransaction;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.BulkCloseAccountsResult;
import org.brite.banking.domain.BulkCloseFailure;
import org.brite.banking.domain.DepositForm;
import org.brite.banking.domain.TransactionHandler;
import org.brite.banking.domain.TransactionType;
import org.brite.banking.domain.WithdrawalForm;
import org.brite.banking.exception.AccountClosedException;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.exception.AgeException;
import org.brite.banking.exception.MaxDepositAmountException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.TransactionRepository;
import org.brite.banking.repository.WithdrawalRepository;
import org.brite.banking.request.AccountLookupRequest;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.request.AccountRegistrationRequest;
import org.brite.banking.request.WithdrawalRequest;
import org.brite.banking.rules.AccountConstraints;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ClientAccountService {
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final WithdrawalMapper withdrawalMapper;
    private final WithdrawalRepository withdrawalRespository;
    private final TransactionRepository transactionRepository;
    private final AccountConstraints accountConstraints;
    private final NotificationService notificationService;
    private final CustomerCredentialService customerCredentialService;

    /**
     * Flow A: Look up consumer account details
     */
    public Optional<Account> lookupAccountDetails(AccountLookupRequest request) {
        log.debug(BankingMessages.LOG_ACCOUNT_LOOKUP, request.getAccountNumber());
        Optional<Account> account = accountRepository.findByAccountNumber(request.getAccountNumber());
        if (account.isEmpty()) {
            log.warn(BankingMessages.LOG_ACCOUNT_LOOKUP_FAILED, request.getAccountNumber());
        }
        return account;
    }

    /**
     * Closes an existing checking or savings account, marking it CLOSED
     * and stamping today as the closure date. Evicts the cached account-search
     * results (AccountStatusStatementService.listAccountStatuses), since accountStatus
     * and closedDate - fields that view exposes - just changed.
     */
    @CacheEvict(cacheNames = AccountStatusStatementService.ACCOUNT_SEARCH_CACHE, allEntries = true)
    public Account closeAccount(String accountNumber) {
        return accountRepository.closeAccount(accountNumber);
    }

    /**
     * Bulk-closes multiple accounts by number, best-effort: an invalid or already-closed
     * account number doesn't block the others from closing. Calls
     * {@code accountRepository.closeAccount} directly (not {@link #closeAccount}) since a
     * self-invocation within the same bean would bypass this method's own {@code @CacheEvict}
     * proxy - evicting once here for the whole batch has the same net effect as evicting
     * per-account, with fewer cache rebuilds.
     */
    @CacheEvict(cacheNames = AccountStatusStatementService.ACCOUNT_SEARCH_CACHE, allEntries = true)
    public BulkCloseAccountsResult closeAccounts(List<String> accountNumbers) {
        log.info(BankingMessages.LOG_BULK_CLOSE_PROCESSING, accountNumbers.size());
        List<Account> closedAccounts = new ArrayList<>();
        List<BulkCloseFailure> failures = new ArrayList<>();
        for (String accountNumber : accountNumbers) {
            try {
                closedAccounts.add(accountRepository.closeAccount(accountNumber));
            } catch (AccountNotFoundException | AccountClosedException e) {
                log.warn(BankingMessages.LOG_BULK_CLOSE_ITEM_FAILED, accountNumber, e.getMessage());
                failures.add(BulkCloseFailure.builder().accountNumber(accountNumber).reason(e.getMessage()).build());
            }
        }
        log.info(BankingMessages.LOG_BULK_CLOSE_COMPLETED, closedAccounts.size(), failures.size());
        return BulkCloseAccountsResult.builder().closedAccounts(closedAccounts).failures(failures).build();
    }

    /**
     * Flow B: Register and save brand new profiles dynamically. Evicts the cached
     * account-search results, since a newly registered account wouldn't otherwise show
     * up in a previously-cached listing for up to the cache's 10-minute TTL.
     */
    @CacheEvict(cacheNames = AccountStatusStatementService.ACCOUNT_SEARCH_CACHE, allEntries = true)
    public Account registerNewClientAccount(AccountRegistrationRequest request) {
        int age = Period.between(request.getDateOfBirth(), LocalDate.now()).getYears();
        int minimumAge = accountConstraints.getMinimumAge();
        if (minimumAge > 0 && age < minimumAge) {
            log.warn(BankingMessages.LOG_REGISTRATION_REJECTED_AGE, age, minimumAge);
            throw new AgeException(String.format(BankingMessages.MINIMUM_AGE_VIOLATION, minimumAge));
        }

        // MapStruct constructs nested object structure automatically
        Account newAccountEntity = accountMapper.toAccountEntity(request);

        // Commits layout back into our static map structure
        Account savedAccount = accountRepository.save(newAccountEntity);
        log.info(BankingMessages.LOG_ACCOUNT_REGISTERED, savedAccount.getAccountType(),
                savedAccount.getCheckingAccountNumber() != null
                        ? savedAccount.getCheckingAccountNumber() : savedAccount.getSavingAccountNumber());
        notificationService.sendEmail(request.getFirstName()+" "+request.getLastName());
        notificationService.sendSms(request.getFirstName()+" "+request.getLastName());
        return savedAccount;
    }

    @Transactional
    public Account withdrawAndSaveToAccount(WithdrawalRequest withdrawalRequest) {
        return withdrawAndSaveToAccount(withdrawalRequest, null);
    }

    /** As above, recording {@code handler} (employee + branch/ATM) on the transaction; null = customer-initiated. */
    @Transactional
    public Account withdrawAndSaveToAccount(WithdrawalRequest withdrawalRequest, TransactionHandler handler) {
        String accountNumber = withdrawalRequest.getAccountNumber();
        BigDecimal withdrawAmount = withdrawalRequest.getWithdrawAmount();

        if (accountNumber == null || accountNumber.length() < 2) {
            log.warn(BankingMessages.LOG_WITHDRAWAL_REJECTED_ACCOUNT_NUMBER);
            throw new IllegalArgumentException(BankingMessages.ACCOUNT_NUMBER_REQUIRED);
        }
        if (withdrawAmount == null || withdrawAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(BankingMessages.LOG_WITHDRAWAL_REJECTED_AMOUNT, accountNumber, withdrawAmount);
            throw new IllegalArgumentException(BankingMessages.WITHDRAWAL_AMOUNT_POSITIVE);
        }

        String prefix = accountNumber.substring(0, 2);
        AccountType requestedAcctType;
        if (prefix.equalsIgnoreCase("CH")) {
            requestedAcctType = AccountType.CHECKING;
        } else if (prefix.equalsIgnoreCase("SV")) {
            requestedAcctType = AccountType.SAVINGS;
        } else {
            log.warn(BankingMessages.LOG_WITHDRAWAL_REJECTED_PREFIX, prefix);
            throw new IllegalArgumentException(String.format(BankingMessages.UNRECOGNIZED_ACCOUNT_PREFIX, prefix));
        }

        if (requestedAcctType != withdrawalRequest.getAccountType()) {
            log.warn(BankingMessages.LOG_WITHDRAWAL_REJECTED_TYPE_MISMATCH,
                    accountNumber, withdrawalRequest.getAccountType(), requestedAcctType);
            throw new IllegalArgumentException(BankingMessages.ACCOUNT_TYPE_MISMATCH);
        }

        requireActiveCustomerLogin(handler, accountNumber);
        log.info(BankingMessages.LOG_WITHDRAWAL_PROCESSING, withdrawAmount, requestedAcctType, accountNumber);

        // Single atomic repository call avoids the find-then-mutate-then-update race
        // between concurrent withdrawals on the same account.
        Account updatedAccount = accountRepository.withdraw(accountNumber, requestedAcctType, withdrawAmount);

        WithdrawalForm historyRecord = new WithdrawalForm(
                accountNumber,
                requestedAcctType,
                LocalDate.now(),
                withdrawAmount,
                "COMPLETED",
                withdrawalRequest.getFirstName(),
                withdrawalRequest.getLastName(),
                withdrawalMapper.toWithdrawalCustomerAddressEntity(withdrawalRequest)
        );
        withdrawalRespository.createWithdrawal(historyRecord);

        BigDecimal balanceAfter = requestedAcctType == AccountType.CHECKING
                ? updatedAccount.getCheckingBalance() : updatedAccount.getSavingBalance();
        transactionRepository.recordTransaction(withHandler(AccountTransaction.builder()
                .accountNumber(accountNumber)
                .transactionType(TransactionType.WITHDRAWAL)
                .amount(withdrawAmount)
                .balanceAfter(balanceAfter)
                .transactionDate(LocalDate.now()), handler).build());

        notificationService.sendEmail(withdrawalRequest.getFirstName()+" "+withdrawalRequest.getLastName());
        notificationService.sendSms(withdrawalRequest.getFirstName()+" "+withdrawalRequest.getLastName());

        return updatedAccount;
    }

    @Transactional
    public Account depositAndSaveToAccount(DepositForm depositForm) {
        return depositAndSaveToAccount(depositForm, null);
    }

    /** As above, recording {@code handler} (employee + branch/ATM) on the transaction; null = customer-initiated. */
    @Transactional
    public Account depositAndSaveToAccount(DepositForm depositForm, TransactionHandler handler) {
        String accountNumber = depositForm.getAccountNumber();
        BigDecimal amount = depositForm.getAmount();

        if (accountNumber == null || accountNumber.length() < 2) {
            log.warn(BankingMessages.LOG_DEPOSIT_REJECTED_ACCOUNT_NUMBER);
            throw new IllegalArgumentException(BankingMessages.ACCOUNT_NUMBER_REQUIRED);
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn(BankingMessages.LOG_DEPOSIT_REJECTED_AMOUNT, accountNumber, amount);
            throw new IllegalArgumentException(BankingMessages.DEPOSIT_AMOUNT_POSITIVE);
        }

        String prefix = accountNumber.substring(0, 2);
        AccountType requestedAcctType;
        if (prefix.equalsIgnoreCase("CH")) {
            requestedAcctType = AccountType.CHECKING;
        } else if (prefix.equalsIgnoreCase("SV")) {
            requestedAcctType = AccountType.SAVINGS;
        } else {
            log.warn(BankingMessages.LOG_DEPOSIT_REJECTED_PREFIX, prefix);
            throw new IllegalArgumentException(String.format(BankingMessages.UNRECOGNIZED_ACCOUNT_PREFIX, prefix));
        }

        if (requestedAcctType != depositForm.getAccountType()) {
            log.warn(BankingMessages.LOG_DEPOSIT_REJECTED_TYPE_MISMATCH,
                    accountNumber, depositForm.getAccountType(), requestedAcctType);
            throw new IllegalArgumentException(BankingMessages.ACCOUNT_TYPE_MISMATCH);
        }

        String depositType = depositForm.getDepositType();
        if (depositType != null && !depositType.equalsIgnoreCase("cash") && !depositType.equalsIgnoreCase("check")) {
            log.warn(BankingMessages.LOG_DEPOSIT_REJECTED_TYPE_INVALID, accountNumber, depositType);
            throw new IllegalArgumentException(BankingMessages.DEPOSIT_TYPE_INVALID);
        }

        BigDecimal maxCashDeposit = accountConstraints.getMaximumDepositAmountByCash();
        if ("cash".equalsIgnoreCase(depositType) && maxCashDeposit != null && amount.compareTo(maxCashDeposit) > 0) {
            log.warn(BankingMessages.LOG_DEPOSIT_REJECTED_MAX_CASH, accountNumber, amount, maxCashDeposit);
            throw new MaxDepositAmountException(String.format(BankingMessages.MAX_CASH_DEPOSIT_EXCEEDED, maxCashDeposit));
        }

        requireActiveCustomerLogin(handler, accountNumber);
        log.info(BankingMessages.LOG_DEPOSIT_PROCESSING, depositType, amount, requestedAcctType, accountNumber);

        // Single atomic repository call avoids the find-then-mutate-then-update race
        // between concurrent deposits on the same account.
        Account updatedAccount = accountRepository.deposit(accountNumber, requestedAcctType, amount);

        BigDecimal balanceAfter = requestedAcctType == AccountType.CHECKING
                ? updatedAccount.getCheckingBalance() : updatedAccount.getSavingBalance();
        transactionRepository.recordTransaction(withHandler(AccountTransaction.builder()
                .accountNumber(accountNumber)
                .transactionType(TransactionType.DEPOSIT)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .transactionDate(LocalDate.now())
                .depositType(depositType), handler).build());

        return updatedAccount;
    }

    /**
     * Customer-initiated transactions (no employee handler) are only allowed while the account owner's login,
     * if they have one, is ACTIVE. Staff-handled ones are gated on the employee's login instead.
     */
    private void requireActiveCustomerLogin(TransactionHandler handler, String accountNumber) {
        if (handler == null) {
            accountRepository.findCustomerIdByAccountNumber(accountNumber)
                    .ifPresent(customerCredentialService::requireActiveLoginIfPresent);
        }
    }

    private static AccountTransaction.AccountTransactionBuilder withHandler(
            AccountTransaction.AccountTransactionBuilder builder, TransactionHandler handler) {
        if (handler == null) {
            return builder;
        }
        return builder
                .employeeNumber(handler.getEmployeeNumber())
                .employeeName(handler.getEmployeeName())
                .employeeRole(handler.getEmployeeRole())
                .bankLocationId(handler.getBankLocationId())
                .bankLocationName(handler.getBankLocationName())
                .bankLocationType(handler.getBankLocationType())
                .bankLocationCity(handler.getBankLocationCity())
                .bankLocationState(handler.getBankLocationState());
    }
}
