package org.brite.banking.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.AccountTransaction;
import org.brite.banking.entity.AccountEntity;
import org.brite.banking.entity.AccountTransactionEntity;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.jpa.AccountJpaRepository;
import org.brite.banking.repository.jpa.AccountTransactionJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;

/**
 * JPA-backed facade over {@link AccountTransactionJpaRepository}, replacing the old
 * in-memory {@code Map<accountNumber, List<AccountTransaction>>}. Maps to/from the
 * existing {@link AccountTransaction} domain object so callers (BankStatementService,
 * ClientAccountService) are unchanged.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class TransactionRepository {
    private final AccountTransactionJpaRepository transactionJpaRepository;
    private final AccountJpaRepository accountJpaRepository;

    @Transactional
    public void recordTransaction(AccountTransaction transaction) {
        Assert.notNull(transaction, "transaction must not be null");
        AccountEntity account = accountJpaRepository.findByAccountNumber(transaction.getAccountNumber())
                .orElseThrow(() -> new AccountNotFoundException(
                        String.format(BankingMessages.ACCOUNT_NOT_FOUND, transaction.getAccountNumber())));
        AccountTransactionEntity entity = AccountTransactionEntity.builder()
                .account(account)
                .transactionType(transaction.getTransactionType())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .transactionDate(transaction.getTransactionDate())
                .depositType(transaction.getDepositType())
                .employeeNumber(transaction.getEmployeeNumber())
                .employeeName(transaction.getEmployeeName())
                .employeeRole(transaction.getEmployeeRole())
                .bankLocationId(transaction.getBankLocationId())
                .bankLocationName(transaction.getBankLocationName())
                .bankLocationType(transaction.getBankLocationType())
                .bankLocationCity(transaction.getBankLocationCity())
                .bankLocationState(transaction.getBankLocationState())
                .build();
        transactionJpaRepository.save(entity);
        log.info(BankingMessages.LOG_TRANSACTION_RECORDED,
                transaction.getTransactionType(), transaction.getAccountNumber(), transaction.getAmount(), transaction.getBalanceAfter());
    }

    @Transactional(readOnly = true)
    public List<AccountTransaction> findByAccountNumber(String accountNumber) {
        return transactionJpaRepository.findByAccount_AccountNumber(accountNumber).stream()
                .map(this::toDomain)
                .toList();
    }

    private AccountTransaction toDomain(AccountTransactionEntity entity) {
        return AccountTransaction.builder()
                .accountNumber(entity.getAccount().getAccountNumber())
                .transactionType(entity.getTransactionType())
                .amount(entity.getAmount())
                .balanceAfter(entity.getBalanceAfter())
                .transactionDate(entity.getTransactionDate())
                .depositType(entity.getDepositType())
                .employeeNumber(entity.getEmployeeNumber())
                .employeeName(entity.getEmployeeName())
                .employeeRole(entity.getEmployeeRole())
                .bankLocationId(entity.getBankLocationId())
                .bankLocationName(entity.getBankLocationName())
                .bankLocationType(entity.getBankLocationType())
                .bankLocationCity(entity.getBankLocationCity())
                .bankLocationState(entity.getBankLocationState())
                .build();
    }
}
