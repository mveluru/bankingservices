package org.brite.banking.exception;

import lombok.extern.slf4j.Slf4j;
import org.brite.banking.messages.BankingMessages;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice(basePackages = "org.brite.banking")
public class BankingExceptionHandler {
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<String> handleAccountNotFound(
            AccountNotFoundException ex) {
        log.warn(BankingMessages.LOG_HANDLER_ACCOUNT_NOT_FOUND, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(LocationNotFoundException.class)
    public ResponseEntity<String> handleLocationNotFound(
            LocationNotFoundException ex) {
        log.warn(BankingMessages.LOG_HANDLER_LOCATION_NOT_FOUND, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<String> handleEmployeeNotFound(
            EmployeeNotFoundException ex) {
        log.warn(BankingMessages.LOG_HANDLER_EMPLOYEE_NOT_FOUND, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ex.getMessage());
    }

    @ExceptionHandler(EmployeeNotAuthorizedException.class)
    public ResponseEntity<String> handleEmployeeNotAuthorized(
            EmployeeNotAuthorizedException ex) {
        log.warn(BankingMessages.LOG_HANDLER_EMPLOYEE_NOT_AUTHORIZED, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ex.getMessage());
    }

    @ExceptionHandler(AccountClosedException.class)
    public ResponseEntity<String> handleAccountClosed(
            AccountClosedException ex) {
        log.warn(BankingMessages.LOG_HANDLER_ACCOUNT_CLOSED, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(AccountSuspendedException.class)
    public ResponseEntity<String> handleAccountSuspended(
            AccountSuspendedException ex) {
        log.warn(BankingMessages.LOG_HANDLER_ACCOUNT_SUSPENDED, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<String> handleInsufficientFunds(
            InsufficientFundsException ex) {
        log.warn(BankingMessages.LOG_HANDLER_INSUFFICIENT_FUNDS, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(
            IllegalArgumentException ex) {
        log.warn(BankingMessages.LOG_HANDLER_INVALID_REQUEST, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(AgeException.class)
    public ResponseEntity<String> handleAgeException(
            AgeException ex) {
        log.warn(BankingMessages.LOG_HANDLER_AGE_VIOLATION, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(MinBalanceException.class)
    public ResponseEntity<String> handleMinBalanceException(
            MinBalanceException ex) {
        log.warn(BankingMessages.LOG_HANDLER_MIN_BALANCE_VIOLATION, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(MaxDepositAmountException.class)
    public ResponseEntity<String> handleMaxDepositAmountException(
            MaxDepositAmountException ex) {
        log.warn(BankingMessages.LOG_HANDLER_MAX_DEPOSIT_VIOLATION, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }

    @ExceptionHandler(StatementRangeExceededException.class)
    public ResponseEntity<String> handleStatementRangeExceededException(
            StatementRangeExceededException ex) {
        log.warn(BankingMessages.LOG_HANDLER_STATEMENT_RANGE_VIOLATION, ex.getMessage());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ex.getMessage());
    }
}
