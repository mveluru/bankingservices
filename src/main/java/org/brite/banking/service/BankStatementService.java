package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.AccountTransaction;
import org.brite.banking.domain.BankStatement;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.exception.StatementRangeExceededException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.AccountRepository;
import org.brite.banking.repository.TransactionRepository;
import org.brite.banking.rules.AccountConstraints;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class BankStatementService {
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountConstraints accountConstraints;
    private final NotificationService notificationService;

    public BankStatement generateStatement(String accountNumber, LocalDate beginDate, LocalDate endDate) {
        if (accountRepository.findByAccountNumber(accountNumber).isEmpty()) {
            throw new AccountNotFoundException(String.format(BankingMessages.ACCOUNT_NOT_FOUND, accountNumber));
        }

        if (endDate.isBefore(beginDate)) {
            log.warn(BankingMessages.LOG_STATEMENT_REJECTED_DATE_ORDER, accountNumber, endDate, beginDate);
            throw new IllegalArgumentException(BankingMessages.STATEMENT_END_BEFORE_BEGIN);
        }

        int maxMonths = accountConstraints.getMaxStatementRangeMonths();
        Period range = Period.between(beginDate, endDate);
        int rangeInMonths = range.getYears() * 12 + range.getMonths();
        if (maxMonths > 0 && rangeInMonths > maxMonths) {
            log.warn(BankingMessages.LOG_STATEMENT_REJECTED_RANGE, accountNumber, beginDate, endDate, maxMonths);
            throw new StatementRangeExceededException(String.format(BankingMessages.STATEMENT_RANGE_EXCEEDED, maxMonths));
        }

        List<AccountTransaction> transactions = transactionRepository.findByAccountNumber(accountNumber).stream()
                .filter(t -> !t.getTransactionDate().isBefore(beginDate) && !t.getTransactionDate().isAfter(endDate))
                .toList();

        log.info(BankingMessages.LOG_STATEMENT_GENERATED, accountNumber, transactions.size(), beginDate, endDate);
        notificationService.sendEmail(accountNumber.substring(0,5)+"xxx");
        notificationService.sendSms(accountNumber.substring(0,5)+"xxx");
        return BankStatement.builder()
                .accountNumber(accountNumber)
                .beginDate(beginDate)
                .endDate(endDate)
                .transactions(transactions)
                .build();
    }
}
