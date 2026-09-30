package org.bee.banking.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bee.banking.domain.AccountStatus;
import org.bee.banking.domain.AccountType;
import org.bee.banking.entity.AccountEntity;
import org.bee.banking.entity.AddressEmbeddable;
import org.bee.banking.entity.CustomerEntity;
import org.bee.banking.repository.jpa.AccountJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Seeds 40 more demo accounts on top of {@link AccountDataSeeder}'s 52: 20 CLOSED (10 checking
 * {@code CH-0000030001..30010}, 10 savings {@code SV-0000040001..40010}) and 20 SUSPENDED (10 checking
 * {@code CH-0000050001..50010}, 10 savings {@code SV-0000060001..60010}).
 * <p>
 * Unlike the base seeder this is idempotent <em>per account number</em> rather than "only if the table is
 * empty", so an existing database that already holds the 52 base accounts still receives these 40 on the next
 * start, and a rerun never duplicates. It depends on the base seeder bean (constructor injection) so an empty database is seeded
 * with the base 52 first (the base seeder skips itself when the table already has any row).
 * <p>
 * Dates are relative to now. The closed accounts were created 26-40 months ago, i.e. outside
 * {@code GET /v1/api/accounts}'s default 18-month window (use {@code months=48} or {@code createdFrom}); the
 * suspended ones were created 4-14 months ago, inside it. 14 suspensions end 3-60 days from now and 6 are
 * indefinite ({@code suspendedEnd} null); none has already expired, so the expiry job leaves them alone.
 * Customers get phone numbers {@code 512-555-0053..0092} (the customer ids used in the SQL mirror
 * {@code db/data/05_seed_closed_and_suspended_accounts.sql}, which holds the same accounts).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountStatusDemoSeeder {
    private final AccountJpaRepository accountJpaRepository;
    /** Injected only to force bean-initialisation order: the base seeder must run (and seed) before this one. */
    @SuppressWarnings("unused")
    private final AccountDataSeeder baseSeeder;

    @PostConstruct
    @Transactional
    public void seedMissing() {
        int before = (int) accountJpaRepository.count();
        log.info("Seeding closed/suspended demo accounts (missing ones only)");

        closed("CH-0000030001", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(26), LocalDate.now().minusMonths(3),
                "Nora", "Adams", LocalDate.of(1965, 1, 1), "512-555-0053", "100 Maple St", "Nashville", "TN", "37201");
        closed("CH-0000030002", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(29), LocalDate.now().minusMonths(5),
                "Owen", "Baker", LocalDate.of(1972, 6, 12), "512-555-0054", "113 Oakwood Ave", "Columbus", "OH", "43215");
        closed("CH-0000030003", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(32), LocalDate.now().minusMonths(7),
                "Priya", "Carter", LocalDate.of(1979, 11, 23), "512-555-0055", "126 Cedar Ln", "Indianapolis", "IN", "46204");
        closed("CH-0000030004", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(35), LocalDate.now().minusMonths(9),
                "Quentin", "Diaz", LocalDate.of(1986, 4, 6), "512-555-0056", "139 Elm Dr", "Charlotte", "NC", "28202");
        closed("CH-0000030005", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(38), LocalDate.now().minusMonths(11),
                "Rosa", "Evans", LocalDate.of(1993, 9, 17), "512-555-0057", "152 Birch Rd", "San Diego", "CA", "92101");
        closed("CH-0000030006", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(26), LocalDate.now().minusMonths(13),
                "Sam", "Flores", LocalDate.of(1965, 2, 28), "512-555-0058", "165 Sunset Blvd", "Las Vegas", "NV", "89101");
        closed("CH-0000030007", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(29), LocalDate.now().minusMonths(15),
                "Tara", "Gray", LocalDate.of(1972, 7, 11), "512-555-0059", "178 Lakeview Ct", "Salt Lake City", "UT", "84101");
        closed("CH-0000030008", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(32), LocalDate.now().minusMonths(17),
                "Umar", "Hughes", LocalDate.of(1979, 12, 22), "512-555-0060", "191 Hillcrest Way", "Minneapolis", "MN", "55401");
        closed("CH-0000030009", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(35), LocalDate.now().minusMonths(19),
                "Vera", "Irwin", LocalDate.of(1986, 5, 5), "512-555-0061", "204 River Rd", "Cleveland", "OH", "44101");
        closed("CH-0000030010", AccountType.CHECKING, new BigDecimal("0.00"), LocalDate.now().minusMonths(38), LocalDate.now().minusMonths(3),
                "Walt", "James", LocalDate.of(1993, 10, 16), "512-555-0062", "217 Park Ave", "Pittsburgh", "PA", "15201");
        closed("SV-0000040001", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(28), LocalDate.now().minusMonths(4),
                "Xena", "Kim", LocalDate.of(1965, 3, 27), "512-555-0063", "230 Maple St", "Baltimore", "MD", "21201");
        closed("SV-0000040002", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(31), LocalDate.now().minusMonths(6),
                "Yuri", "Lopez", LocalDate.of(1972, 8, 10), "512-555-0064", "243 Oakwood Ave", "Milwaukee", "WI", "53202");
        closed("SV-0000040003", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(34), LocalDate.now().minusMonths(8),
                "Zelda", "Moore", LocalDate.of(1979, 1, 21), "512-555-0065", "256 Cedar Ln", "Sacramento", "CA", "95814");
        closed("SV-0000040004", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(37), LocalDate.now().minusMonths(10),
                "Andre", "Nguyen", LocalDate.of(1986, 6, 4), "512-555-0066", "269 Elm Dr", "Orlando", "FL", "32801");
        closed("SV-0000040005", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(40), LocalDate.now().minusMonths(12),
                "Bianca", "Owens", LocalDate.of(1993, 11, 15), "512-555-0067", "282 Birch Rd", "Memphis", "TN", "38103");
        closed("SV-0000040006", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(29), LocalDate.now().minusMonths(14),
                "Caleb", "Patel", LocalDate.of(1965, 4, 26), "512-555-0068", "295 Sunset Blvd", "Birmingham", "AL", "35203");
        closed("SV-0000040007", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(32), LocalDate.now().minusMonths(16),
                "Dina", "Quincy", LocalDate.of(1972, 9, 9), "512-555-0069", "308 Lakeview Ct", "Des Moines", "IA", "50309");
        closed("SV-0000040008", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(35), LocalDate.now().minusMonths(18),
                "Emeka", "Rivera", LocalDate.of(1979, 2, 20), "512-555-0070", "321 Hillcrest Way", "Little Rock", "AR", "72201");
        closed("SV-0000040009", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(38), LocalDate.now().minusMonths(20),
                "Farah", "Stone", LocalDate.of(1986, 7, 3), "512-555-0071", "334 River Rd", "Jackson", "MS", "39201");
        closed("SV-0000040010", AccountType.SAVINGS, new BigDecimal("0.00"), LocalDate.now().minusMonths(41), LocalDate.now().minusMonths(5),
                "Gustavo", "Turner", LocalDate.of(1993, 12, 14), "512-555-0072", "347 Park Ave", "Honolulu", "HI", "96813");
        suspended("CH-0000050001", AccountType.CHECKING, new BigDecimal("5808.93"), LocalDate.now().minusMonths(4),
                LocalDateTime.now().minusDays(1), null, "Suspected fraudulent card activity - under review",
                "Hana", "Usman", LocalDate.of(1965, 5, 25), "512-555-0073", "360 Maple St", "Nashville", "TN", "37201");
        suspended("CH-0000050002", AccountType.CHECKING, new BigDecimal("371.35"), LocalDate.now().minusMonths(6),
                LocalDateTime.now().minusDays(4), LocalDateTime.now().plusDays(7), "KYC documents expired - awaiting updated ID",
                "Ivan", "Vance", LocalDate.of(1972, 10, 8), "512-555-0074", "373 Oakwood Ave", "Columbus", "OH", "43215");
        suspended("CH-0000050003", AccountType.CHECKING, new BigDecimal("2584.01"), LocalDate.now().minusMonths(8),
                LocalDateTime.now().minusDays(7), LocalDateTime.now().plusDays(11), "Customer requested temporary freeze (lost debit card)",
                "Jada", "Walker", LocalDate.of(1979, 3, 19), "512-555-0075", "386 Cedar Ln", "Indianapolis", "IN", "46204");
        suspended("CH-0000050004", AccountType.CHECKING, new BigDecimal("2125.42"), LocalDate.now().minusMonths(10),
                LocalDateTime.now().minusDays(10), null, "Court order - funds frozen pending legal review",
                "Kofi", "Xu", LocalDate.of(1986, 8, 2), "512-555-0076", "399 Elm Dr", "Charlotte", "NC", "28202");
        suspended("CH-0000050005", AccountType.CHECKING, new BigDecimal("6667.77"), LocalDate.now().minusMonths(12),
                LocalDateTime.now().minusDays(13), LocalDateTime.now().plusDays(19), "Compliance hold - unusual wire activity",
                "Lena", "Young", LocalDate.of(1993, 1, 13), "512-555-0077", "412 Birch Rd", "San Diego", "CA", "92101");
        suspended("CH-0000050006", AccountType.CHECKING, new BigDecimal("6138.79"), LocalDate.now().minusMonths(14),
                LocalDateTime.now().minusDays(16), LocalDateTime.now().plusDays(23), "Returned mail - address verification required",
                "Mateo", "Zimmer", LocalDate.of(1965, 6, 24), "512-555-0078", "425 Sunset Blvd", "Las Vegas", "NV", "89101");
        suspended("CH-0000050007", AccountType.CHECKING, new BigDecimal("8045.79"), LocalDate.now().minusMonths(5),
                LocalDateTime.now().minusDays(19), null, "Possible account takeover reported by customer",
                "Nadia", "Allen", LocalDate.of(1972, 11, 7), "512-555-0079", "438 Lakeview Ct", "Salt Lake City", "UT", "84101");
        suspended("CH-0000050008", AccountType.CHECKING, new BigDecimal("919.41"), LocalDate.now().minusMonths(7),
                LocalDateTime.now().minusDays(22), LocalDateTime.now().plusDays(31), "Chargeback dispute under investigation",
                "Omar", "Bennett", LocalDate.of(1979, 4, 18), "512-555-0080", "451 Hillcrest Way", "Minneapolis", "MN", "55401");
        suspended("CH-0000050009", AccountType.CHECKING, new BigDecimal("3884.01"), LocalDate.now().minusMonths(9),
                LocalDateTime.now().minusDays(25), LocalDateTime.now().plusDays(35), "Sanctions screening alert - manual review",
                "Petra", "Cooper", LocalDate.of(1986, 9, 1), "512-555-0081", "464 River Rd", "Cleveland", "OH", "44101");
        suspended("CH-0000050010", AccountType.CHECKING, new BigDecimal("413.71"), LocalDate.now().minusMonths(11),
                LocalDateTime.now().minusDays(28), null, "Customer traveling abroad - requested temporary hold",
                "Quinn", "Dixon", LocalDate.of(1993, 2, 12), "512-555-0082", "477 Park Ave", "Pittsburgh", "PA", "15201");
        suspended("SV-0000060001", AccountType.SAVINGS, new BigDecimal("5856.63"), LocalDate.now().minusMonths(13),
                LocalDateTime.now().minusDays(31), LocalDateTime.now().plusDays(43), "Suspected fraudulent card activity - under review",
                "Rafael", "Ellis", LocalDate.of(1965, 7, 23), "512-555-0083", "490 Maple St", "Baltimore", "MD", "21201");
        suspended("SV-0000060002", AccountType.SAVINGS, new BigDecimal("12881.20"), LocalDate.now().minusMonths(4),
                LocalDateTime.now().minusDays(34), LocalDateTime.now().plusDays(47), "KYC documents expired - awaiting updated ID",
                "Sofia", "Fisher", LocalDate.of(1972, 12, 6), "512-555-0084", "503 Oakwood Ave", "Milwaukee", "WI", "53202");
        suspended("SV-0000060003", AccountType.SAVINGS, new BigDecimal("1150.13"), LocalDate.now().minusMonths(6),
                LocalDateTime.now().minusDays(37), null, "Customer requested temporary freeze (lost debit card)",
                "Tomas", "Grant", LocalDate.of(1979, 5, 17), "512-555-0085", "516 Cedar Ln", "Sacramento", "CA", "95814");
        suspended("SV-0000060004", AccountType.SAVINGS, new BigDecimal("5371.52"), LocalDate.now().minusMonths(8),
                LocalDateTime.now().minusDays(40), LocalDateTime.now().plusDays(55), "Court order - funds frozen pending legal review",
                "Uma", "Hayes", LocalDate.of(1986, 10, 28), "512-555-0086", "529 Elm Dr", "Orlando", "FL", "32801");
        suspended("SV-0000060005", AccountType.SAVINGS, new BigDecimal("16422.17"), LocalDate.now().minusMonths(10),
                LocalDateTime.now().minusDays(3), LocalDateTime.now().plusDays(59), "Compliance hold - unusual wire activity",
                "Victor", "Ingram", LocalDate.of(1993, 3, 11), "512-555-0087", "542 Birch Rd", "Memphis", "TN", "38103");
        suspended("SV-0000060006", AccountType.SAVINGS, new BigDecimal("13851.07"), LocalDate.now().minusMonths(12),
                LocalDateTime.now().minusDays(6), null, "Returned mail - address verification required",
                "Willa", "Jenkins", LocalDate.of(1965, 8, 22), "512-555-0088", "555 Sunset Blvd", "Birmingham", "AL", "35203");
        suspended("SV-0000060007", AccountType.SAVINGS, new BigDecimal("5900.80"), LocalDate.now().minusMonths(14),
                LocalDateTime.now().minusDays(9), LocalDateTime.now().plusDays(9), "Possible account takeover reported by customer",
                "Ximena", "Knight", LocalDate.of(1972, 1, 5), "512-555-0089", "568 Lakeview Ct", "Des Moines", "IA", "50309");
        suspended("SV-0000060008", AccountType.SAVINGS, new BigDecimal("14937.01"), LocalDate.now().minusMonths(5),
                LocalDateTime.now().minusDays(12), LocalDateTime.now().plusDays(13), "Chargeback dispute under investigation",
                "Yara", "Lawson", LocalDate.of(1979, 6, 16), "512-555-0090", "581 Hillcrest Way", "Little Rock", "AR", "72201");
        suspended("SV-0000060009", AccountType.SAVINGS, new BigDecimal("20331.05"), LocalDate.now().minusMonths(7),
                LocalDateTime.now().minusDays(15), LocalDateTime.now().plusDays(17), "Sanctions screening alert - manual review",
                "Zane", "Mendez", LocalDate.of(1986, 11, 27), "512-555-0091", "594 River Rd", "Jackson", "MS", "39201");
        suspended("SV-0000060010", AccountType.SAVINGS, new BigDecimal("659.22"), LocalDate.now().minusMonths(9),
                LocalDateTime.now().minusDays(18), LocalDateTime.now().plusDays(21), "Customer traveling abroad - requested temporary hold",
                "Amara", "Norris", LocalDate.of(1993, 4, 10), "512-555-0092", "607 Park Ave", "Honolulu", "HI", "96813");

        log.info("Seeded {} closed/suspended demo accounts", accountJpaRepository.count() - before);
    }

    private void closed(String accountNumber, AccountType type, BigDecimal balance, LocalDate createdDate, LocalDate closedDate,
                        String firstName, String lastName, LocalDate dateOfBirth, String phoneNumber,
                        String street, String city, String state, String zip) {
        save(AccountEntity.builder()
                .accountNumber(accountNumber).accountType(type).accountStatus(AccountStatus.CLOSED)
                .balance(balance).createdDate(createdDate).closedDate(closedDate),
                firstName, lastName, dateOfBirth, phoneNumber, street, city, state, zip);
    }

    private void suspended(String accountNumber, AccountType type, BigDecimal balance, LocalDate createdDate,
                           LocalDateTime suspendedStart, LocalDateTime suspendedEnd, String notes,
                           String firstName, String lastName, LocalDate dateOfBirth, String phoneNumber,
                           String street, String city, String state, String zip) {
        save(AccountEntity.builder()
                .accountNumber(accountNumber).accountType(type).accountStatus(AccountStatus.SUSPENDED)
                .balance(balance).createdDate(createdDate)
                .suspended(true).suspendedStart(suspendedStart).suspendedEnd(suspendedEnd).suspensionNotes(notes),
                firstName, lastName, dateOfBirth, phoneNumber, street, city, state, zip);
    }

    private void save(AccountEntity.AccountEntityBuilder account, String firstName, String lastName, LocalDate dateOfBirth,
                      String phoneNumber, String street, String city, String state, String zip) {
        AccountEntity entity = account.build();
        if (accountJpaRepository.findByAccountNumber(entity.getAccountNumber()).isPresent()) {
            return;
        }
        entity.setCustomer(CustomerEntity.builder()
                .firstName(firstName).lastName(lastName).dateOfBirth(dateOfBirth).phoneNumber(phoneNumber)
                .address(AddressEmbeddable.builder()
                        .street(street).city(city).state(state).zip(zip).country("USA").addressLine1(street)
                        .build())
                .build());
        accountJpaRepository.save(entity);
    }
}
