package org.bee.banking.repository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bee.banking.domain.Account;
import org.bee.banking.domain.AccountStatus;
import org.bee.banking.domain.AccountType;
import org.bee.banking.domain.Address;
import org.bee.banking.domain.Customer;
import org.bee.banking.entity.AccountEntity;
import org.bee.banking.entity.AddressEmbeddable;
import org.bee.banking.entity.CustomerEntity;
import org.bee.banking.exception.AccountClosedException;
import org.bee.banking.exception.AccountNotFoundException;
import org.bee.banking.exception.AccountSuspendedException;
import org.bee.banking.exception.InsufficientFundsException;
import org.bee.banking.exception.MinBalanceException;
import org.bee.banking.messages.BankingMessages;
import org.bee.banking.repository.jpa.AccountJpaRepository;
import org.bee.banking.rules.AccountConstraints;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * JPA-backed facade over {@link AccountJpaRepository}: the service layer keeps using the
 * {@link Account} domain object (which conflates a checking and a savings account into
 * one object with a pair of nullable fields, a shape kept for backward compatibility with
 * existing callers/tests) while this class maps to/from {@link AccountEntity}, which is
 * one row per real account. See {@link AccountEntity} for why the two shapes differ.
 */
@Repository
@Slf4j
@RequiredArgsConstructor
public class AccountRepository {
    private final AccountJpaRepository accountJpaRepository;
    private final AccountConstraints accountConstraints;

    public Optional<Account> findByAccountNumber(String accountNumber) {
        if (accountNumber == null) return Optional.empty();
        return accountJpaRepository.findByAccountNumber(accountNumber).map(this::toDomain);
    }

    /**
     * Filters accounts by an exact account number, status, and/or a createdDate/closedDate
     * range via a dynamic {@link Specification}, letting the database do the filtering,
     * sorting, and pagination instead of hand-rolled Java streams. When {@code accountNumber}
     * is given, every other filter still applies - the result is a single-element (or empty)
     * page rather than a special case, so callers get one consistent paginated shape either way.
     */
    @Transactional(readOnly = true)
    public Page<Account> search(String accountNumber, AccountStatus status, LocalDate createdFrom, LocalDate createdTo,
                                 LocalDate closedFrom, LocalDate closedTo, Pageable pageable) {
        for (Sort.Order order : pageable.getSort()) {
            validateSortProperty(order.getProperty());
        }

        Specification<AccountEntity> spec = Specification.unrestricted();
        if (accountNumber != null) {
            String needle = accountNumber;
            spec = spec.and((root, query, cb) -> cb.equal(cb.lower(root.get("accountNumber")), needle.toLowerCase()));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("accountStatus"), status));
        }
        if (createdFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdDate"), createdFrom));
        }
        if (createdTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdDate"), createdTo));
        }
        if (closedFrom != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("closedDate"), closedFrom));
        }
        if (closedTo != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("closedDate"), closedTo));
        }

        return accountJpaRepository.findAll(spec, pageable).map(this::toDomain);
    }

    private void validateSortProperty(String property) {
        switch (property) {
            case "createdDate", "closedDate", "accountStatus", "accountNumber" -> { }
            default -> throw new IllegalArgumentException(String.format(BankingMessages.UNSUPPORTED_SORT_PROPERTY, property));
        }
    }

    @Transactional
    public Account save(Account account) {
        boolean isNew = account.getCheckingAccountNumber() == null && account.getSavingAccountNumber() == null;
        AccountEntity entity;
        if (isNew) {
            // Prefix must be "CH" or "SV" - withdraw/deposit resolve account type from it.
            // Numeric part is zero-padded to at least 10 digits to match the seeded numbering scheme.
            String prefix = account.getAccountType() == AccountType.CHECKING ? "CH" : "SV";
            String generatedNum = prefix + "-" + String.format("%010d", accountJpaRepository.count() + 10001);
            entity = AccountEntity.builder()
                    .accountNumber(generatedNum)
                    .accountType(account.getAccountType())
                    .accountStatus(account.getAccountStatus() != null ? account.getAccountStatus() : AccountStatus.ACTIVE)
                    .createdDate(account.getCreatedDate() != null ? account.getCreatedDate() : LocalDate.now())
                    .closedDate(account.getClosedDate())
                    .balance(BigDecimal.ZERO)
                    .customer(toEntity(account.getCustomer()))
                    .build();
            log.info(BankingMessages.LOG_ACCOUNT_NUMBER_GENERATED, account.getAccountType(), generatedNum);
        } else {
            String existingNumber = account.getCheckingAccountNumber() != null
                    ? account.getCheckingAccountNumber() : account.getSavingAccountNumber();
            entity = accountJpaRepository.findByAccountNumber(existingNumber)
                    .orElseThrow(() -> new AccountNotFoundException(String.format(BankingMessages.ACCOUNT_NOT_FOUND, existingNumber)));
        }

        AccountEntity saved = accountJpaRepository.save(entity);
        log.info(BankingMessages.LOG_ACCOUNT_SAVED, saved.getAccountNumber());
        return toDomain(saved);
    }

    /**
     * Debits the checking or savings balance inside a transaction; {@code @Version} on
     * {@link AccountEntity} makes concurrent withdrawals on the same row fail fast with
     * an optimistic-locking exception instead of silently losing an update, replacing the
     * old in-memory version's atomic {@code ConcurrentHashMap.computeIfPresent}.
     */
    @Transactional
    public Account withdraw(String accountNumber, AccountType accountType, BigDecimal amount) {
        AccountEntity entity = accountJpaRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn(BankingMessages.LOG_WITHDRAWAL_ACCOUNT_NOT_FOUND, accountNumber);
                    return new AccountNotFoundException(String.format(BankingMessages.ACCOUNT_NOT_FOUND, accountNumber));
                });
        if (entity.getAccountStatus() == AccountStatus.CLOSED) {
            log.warn(BankingMessages.LOG_WITHDRAWAL_REJECTED_CLOSED, accountNumber);
            throw new AccountClosedException(String.format(BankingMessages.ACCOUNT_CLOSED, accountNumber));
        }
        if (isSuspended(entity)) {
            log.warn(BankingMessages.LOG_WITHDRAWAL_REJECTED_SUSPENDED, accountNumber);
            throw new AccountSuspendedException(String.format(BankingMessages.ACCOUNT_SUSPENDED, accountNumber));
        }
        BigDecimal currentBalance = entity.getBalance();
        if (currentBalance == null || currentBalance.compareTo(amount) < 0) {
            log.warn(BankingMessages.LOG_WITHDRAWAL_INSUFFICIENT_FUNDS, amount, accountNumber, currentBalance);
            throw new InsufficientFundsException(String.format(BankingMessages.INSUFFICIENT_FUNDS, accountNumber));
        }
        BigDecimal newBalance = currentBalance.subtract(amount);
        BigDecimal minimumBalance = accountType == AccountType.CHECKING
                ? accountConstraints.getCheckingMinimumBalance()
                : accountConstraints.getSavingMinimumBalance();
        if (minimumBalance != null && newBalance.compareTo(minimumBalance) < 0) {
            log.warn(BankingMessages.LOG_WITHDRAWAL_BELOW_MINIMUM_BALANCE, amount, accountNumber, newBalance, minimumBalance);
            throw new MinBalanceException(String.format(BankingMessages.MIN_BALANCE_VIOLATION, accountNumber, minimumBalance));
        }
        entity.setBalance(newBalance);
        AccountEntity saved = accountJpaRepository.save(entity);
        log.info(BankingMessages.LOG_WITHDRAWAL_SUCCESS, amount, accountType, accountNumber);
        return toDomain(saved);
    }

    @Transactional
    public Account deposit(String accountNumber, AccountType accountType, BigDecimal amount) {
        AccountEntity entity = accountJpaRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn(BankingMessages.LOG_DEPOSIT_ACCOUNT_NOT_FOUND, accountNumber);
                    return new AccountNotFoundException(String.format(BankingMessages.ACCOUNT_NOT_FOUND, accountNumber));
                });
        if (entity.getAccountStatus() == AccountStatus.CLOSED) {
            log.warn(BankingMessages.LOG_DEPOSIT_REJECTED_CLOSED, accountNumber);
            throw new AccountClosedException(String.format(BankingMessages.ACCOUNT_CLOSED, accountNumber));
        }
        if (isSuspended(entity)) {
            log.warn(BankingMessages.LOG_DEPOSIT_REJECTED_SUSPENDED, accountNumber);
            throw new AccountSuspendedException(String.format(BankingMessages.ACCOUNT_SUSPENDED, accountNumber));
        }
        BigDecimal currentBalance = entity.getBalance() != null ? entity.getBalance() : BigDecimal.ZERO;
        entity.setBalance(currentBalance.add(amount));
        AccountEntity saved = accountJpaRepository.save(entity);
        log.info(BankingMessages.LOG_DEPOSIT_SUCCESS, amount, accountType, accountNumber);
        return toDomain(saved);
    }

    @Transactional
    public Account closeAccount(String accountNumber) {
        AccountEntity entity = accountJpaRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn(BankingMessages.LOG_ACCOUNT_CLOSE_ACCOUNT_NOT_FOUND, accountNumber);
                    return new AccountNotFoundException(String.format(BankingMessages.ACCOUNT_NOT_FOUND, accountNumber));
                });
        if (entity.getAccountStatus() == AccountStatus.CLOSED) {
            log.warn(BankingMessages.LOG_ACCOUNT_CLOSE_REJECTED_ALREADY_CLOSED, accountNumber);
            throw new AccountClosedException(String.format(BankingMessages.ACCOUNT_ALREADY_CLOSED, accountNumber));
        }
        entity.setAccountStatus(AccountStatus.CLOSED);
        entity.setClosedDate(LocalDate.now());
        clearSuspension(entity); // a closed account is no longer "suspended"
        AccountEntity saved = accountJpaRepository.save(entity);
        log.info(BankingMessages.LOG_ACCOUNT_CLOSED, accountNumber);
        return toDomain(saved);
    }

    /**
     * ACTIVE -> SUSPENDED. The caller (service) has already resolved {@code start} and validated the
     * time window; this checks the account's state. A CLOSED account can't be suspended and an
     * already-suspended one must be updated or reactivated first.
     */
    @Transactional
    public Account suspend(String accountNumber, LocalDateTime start, LocalDateTime end, String notes) {
        AccountEntity entity = accountJpaRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> {
                    log.warn(BankingMessages.LOG_ACCOUNT_SUSPEND_ACCOUNT_NOT_FOUND, accountNumber);
                    return new AccountNotFoundException(String.format(BankingMessages.ACCOUNT_NOT_FOUND, accountNumber));
                });
        if (entity.getAccountStatus() == AccountStatus.CLOSED) {
            log.warn(BankingMessages.LOG_ACCOUNT_SUSPEND_REJECTED, accountNumber, "closed");
            throw new AccountClosedException(String.format(BankingMessages.ACCOUNT_CLOSED_CANNOT_SUSPEND, accountNumber));
        }
        if (isSuspended(entity)) {
            log.warn(BankingMessages.LOG_ACCOUNT_SUSPEND_REJECTED, accountNumber, "already suspended");
            throw new AccountSuspendedException(String.format(BankingMessages.ACCOUNT_ALREADY_SUSPENDED, accountNumber));
        }
        entity.setAccountStatus(AccountStatus.SUSPENDED);
        entity.setSuspended(true);
        entity.setSuspendedStart(start);
        entity.setSuspendedEnd(end);
        entity.setSuspensionNotes(notes);
        AccountEntity saved = accountJpaRepository.save(entity);
        log.info(BankingMessages.LOG_ACCOUNT_SUSPENDED, accountNumber, start, end);
        return toDomain(saved);
    }

    /**
     * Changes the end and/or notes of a current suspension (null = leave unchanged). The new end must
     * be after the stored start, which only the stored row knows, so that check lives here.
     */
    @Transactional
    public Account updateSuspension(String accountNumber, LocalDateTime newEnd, String newNotes) {
        AccountEntity entity = requireSuspended(accountNumber);
        if (newEnd != null) {
            if (!newEnd.isAfter(entity.getSuspendedStart())) {
                throw new IllegalArgumentException(String.format(
                        BankingMessages.SUSPENSION_END_NOT_AFTER_START, newEnd, entity.getSuspendedStart()));
            }
            entity.setSuspendedEnd(newEnd);
        }
        if (newNotes != null) {
            entity.setSuspensionNotes(newNotes);
        }
        AccountEntity saved = accountJpaRepository.save(entity);
        log.info(BankingMessages.LOG_SUSPENSION_UPDATED, accountNumber, saved.getSuspendedEnd(), newNotes != null);
        return toDomain(saved);
    }

    /** SUSPENDED -> ACTIVE; clears the suspension fields. */
    @Transactional
    public Account reactivate(String accountNumber) {
        AccountEntity entity = requireSuspended(accountNumber);
        entity.setAccountStatus(AccountStatus.ACTIVE);
        clearSuspension(entity);
        AccountEntity saved = accountJpaRepository.save(entity);
        log.info(BankingMessages.LOG_ACCOUNT_REACTIVATED, accountNumber);
        return toDomain(saved);
    }

    /** Reactivates every suspension whose end is at or before {@code now}; returns how many. */
    @Transactional
    public int reactivateExpiredSuspensions(LocalDateTime now) {
        List<AccountEntity> expired = accountJpaRepository.findBySuspendedTrueAndSuspendedEndLessThanEqual(now);
        for (AccountEntity entity : expired) {
            entity.setAccountStatus(AccountStatus.ACTIVE);
            clearSuspension(entity);
        }
        accountJpaRepository.saveAll(expired);
        if (!expired.isEmpty()) {
            log.info(BankingMessages.LOG_SUSPENSIONS_EXPIRED, expired.size());
        }
        return expired.size();
    }

    private AccountEntity requireSuspended(String accountNumber) {
        AccountEntity entity = accountJpaRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(String.format(BankingMessages.ACCOUNT_NOT_FOUND, accountNumber)));
        if (!isSuspended(entity)) {
            throw new IllegalArgumentException(String.format(BankingMessages.ACCOUNT_NOT_SUSPENDED, accountNumber));
        }
        return entity;
    }

    /** Status and flag are always written together; either one being set blocks transactions. */
    private boolean isSuspended(AccountEntity entity) {
        return entity.getAccountStatus() == AccountStatus.SUSPENDED || entity.isSuspended();
    }

    private void clearSuspension(AccountEntity entity) {
        entity.setSuspended(false);
        entity.setSuspendedStart(null);
        entity.setSuspendedEnd(null);
        entity.setSuspensionNotes(null);
    }

    private Account toDomain(AccountEntity entity) {
        Account.AccountBuilder builder = Account.builder()
                .accountType(entity.getAccountType())
                .accountStatus(entity.getAccountStatus())
                .createdDate(entity.getCreatedDate())
                .closedDate(entity.getClosedDate())
                .suspended(entity.isSuspended())
                .suspendedStart(entity.getSuspendedStart())
                .suspendedEnd(entity.getSuspendedEnd())
                .suspensionNotes(entity.getSuspensionNotes())
                .customer(toDomain(entity.getCustomer()));
        if (entity.getAccountType() == AccountType.SAVINGS) {
            builder.savingAccountNumber(entity.getAccountNumber()).savingBalance(entity.getBalance());
        } else {
            builder.checkingAccountNumber(entity.getAccountNumber()).checkingBalance(entity.getBalance());
        }
        return builder.build();
    }

    private Customer toDomain(CustomerEntity entity) {
        if (entity == null) return null;
        return Customer.builder()
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .dateOfBirth(entity.getDateOfBirth())
                .phoneNumber(entity.getPhoneNumber())
                .address(toDomain(entity.getAddress()))
                .build();
    }

    private Address toDomain(AddressEmbeddable embeddable) {
        if (embeddable == null) return null;
        return Address.builder()
                .street(embeddable.getStreet())
                .city(embeddable.getCity())
                .state(embeddable.getState())
                .zip(embeddable.getZip())
                .country(embeddable.getCountry())
                .addressLine1(embeddable.getAddressLine1())
                .addressLine2(embeddable.getAddressLine2())
                .build();
    }

    private CustomerEntity toEntity(Customer customer) {
        if (customer == null) return null;
        return CustomerEntity.builder()
                .firstName(customer.getFirstName())
                .lastName(customer.getLastName())
                .dateOfBirth(customer.getDateOfBirth())
                .phoneNumber(customer.getPhoneNumber())
                .address(toEntity(customer.getAddress()))
                .build();
    }

    private AddressEmbeddable toEntity(Address address) {
        if (address == null) return null;
        return AddressEmbeddable.builder()
                .street(address.getStreet())
                .city(address.getCity())
                .state(address.getState())
                .zip(address.getZip())
                .country(address.getCountry())
                .addressLine1(address.getAddressLine1())
                .addressLine2(address.getAddressLine2())
                .build();
    }
}
