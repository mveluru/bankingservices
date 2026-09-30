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

/**
 * Seeds 52 demo accounts (26 checking, 26 savings) into the database on startup, but
 * only if the accounts table is empty. Account numbers are always a "CH-"/"SV-" prefix
 * plus a zero-padded 10-digit number (see {@link AccountRepository#save}, which pads
 * dynamically-generated numbers the same way). createdDate/closedDate are computed
 * relative to LocalDate.now() (not fixed calendar dates), so the seeded "plain" ACTIVE
 * accounts always fall inside AccountStatusStatementService's default 18-month lookback
 * window regardless of when the app is started, while the two seeded CLOSED accounts
 * (CH-0000010004, SV-0000020004) are deliberately older than that window to demonstrate
 * the date-range/status filters needing an explicit range or a wider `months` value.
 * <p>
 * This replaces the old AccountRepository constructor's re-seed-every-restart behavior:
 * since accounts now persist in real MySQL, re-seeding unconditionally would create
 * duplicates on every restart, so this only runs once per fresh database.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccountDataSeeder {
    private final AccountJpaRepository accountJpaRepository;
    /** Customers are seeded in order 1..52; customer N gets phone 512-555-000N (same as db/data/01_seed_customers_accounts.sql). */
    private int customerSequence;

    @PostConstruct
    @Transactional
    public void seedIfEmpty() {
        if (accountJpaRepository.count() > 0) {
            log.info("Accounts table already has data; skipping demo seed");
            return;
        }
        log.info("Seeding demo banking accounts");

        seedChecking("CH-0000088291", new BigDecimal("2450.75"), AccountStatus.ACTIVE, LocalDate.now().minusMonths(2), null,
                "Alice", "Smith", LocalDate.of(1985, 4, 12), "123 Main St", "Austin", "TX", "78701", "Apt 4B");
        seedSavings("SV-0000044102", new BigDecimal("12800.00"), AccountStatus.ACTIVE, LocalDate.now().minusMonths(4), null,
                "Bob", "Jones", LocalDate.of(1991, 11, 23), "456 Oak Ln", "Dallas", "TX", "75201", "456 Oak Ln");

        LocalDate plainCreated = LocalDate.now().minusMonths(5);
        seedChecking("CH-0000010001", new BigDecimal("3200.50"), AccountStatus.ACTIVE, plainCreated, null, "Carol", "Davis", LocalDate.of(1978, 3, 15), "500 5th Ave", "Denver", "CO", "80202", "500 5th Ave");
        seedChecking("CH-0000010002", new BigDecimal("1875.20"), AccountStatus.ACTIVE, plainCreated, null, "David", "Miller", LocalDate.of(1994, 8, 19), "789 Pine Rd", "Houston", "TX", "77001", "789 Pine Rd");
        seedChecking("CH-0000010003", new BigDecimal("4620.00"), AccountStatus.ACTIVE, plainCreated, null, "Emma", "Wilson", LocalDate.of(1988, 12, 1), "200 2nd St", "Seattle", "WA", "98101", "200 2nd St");
        seedChecking("CH-0000010004", new BigDecimal("980.35"), AccountStatus.CLOSED, LocalDate.now().minusMonths(30), LocalDate.now().minusMonths(6), "Frank", "Garcia", LocalDate.of(1975, 6, 23), "100 Ocean Dr", "Miami", "FL", "33101", "100 Ocean Dr");
        seedChecking("CH-0000010005", new BigDecimal("6120.75"), AccountStatus.ACTIVE, plainCreated, null, "Grace", "Lee", LocalDate.of(1990, 1, 30), "300 Lake Shore Dr", "Chicago", "IL", "60601", "300 Lake Shore Dr");
        seedChecking("CH-0000010006", new BigDecimal("2340.60"), AccountStatus.ACTIVE, plainCreated, null, "Henry", "Martinez", LocalDate.of(1982, 9, 5), "150 Desert Rd", "Phoenix", "AZ", "85001", "150 Desert Rd");
        seedChecking("CH-0000010007", new BigDecimal("1500.00"), AccountStatus.ACTIVE, plainCreated, null, "Ivy", "Chen", LocalDate.of(1996, 4, 11), "45 Beacon St", "Boston", "MA", "02101", "45 Beacon St");
        seedChecking("CH-0000010008", new BigDecimal("7890.10"), AccountStatus.ACTIVE, plainCreated, null, "Jack", "Robinson", LocalDate.of(1970, 11, 27), "10 Peachtree St", "Atlanta", "GA", "30301", "10 Peachtree St");
        seedChecking("CH-0000010009", new BigDecimal("3450.90"), AccountStatus.ACTIVE, plainCreated, null, "Karen", "White", LocalDate.of(1985, 7, 14), "25 Pine St", "Portland", "OR", "97201", "25 Pine St");
        seedChecking("CH-0000010010", new BigDecimal("2100.45"), AccountStatus.ACTIVE, plainCreated, null, "Liam", "Thompson", LocalDate.of(1993, 2, 8), "600 Colfax Ave", "Denver", "CO", "80203", "600 Colfax Ave");
        seedChecking("CH-0000010011", new BigDecimal("5430.00"), AccountStatus.ACTIVE, plainCreated, null, "Xavier", "Brooks", LocalDate.of(1986, 5, 21), "12 Birch St", "Raleigh", "NC", "27601", "12 Birch St");
        seedChecking("CH-0000010012", new BigDecimal("2890.60"), AccountStatus.ACTIVE, plainCreated, null, "Yolanda", "Reyes", LocalDate.of(1991, 9, 14), "77 Cedar Ave", "Tucson", "AZ", "85701", "77 Cedar Ave");
        seedChecking("CH-0000010013", new BigDecimal("6710.25"), AccountStatus.ACTIVE, plainCreated, null, "Zachary", "Foster", LocalDate.of(1979, 2, 3), "300 Elm St", "Kansas City", "MO", "64101", "300 Elm St");
        seedChecking("CH-0000010014", new BigDecimal("1980.40"), AccountStatus.ACTIVE, plainCreated, null, "Amanda", "Price", LocalDate.of(1997, 7, 30), "88 Willow Dr", "Omaha", "NE", "68101", "88 Willow Dr");
        seedChecking("CH-0000010015", new BigDecimal("4560.15"), AccountStatus.ACTIVE, plainCreated, null, "Brian", "Coleman", LocalDate.of(1984, 11, 8), "45 Aspen Ln", "Boise", "ID", "83701", "45 Aspen Ln");
        seedChecking("CH-0000010016", new BigDecimal("3320.90"), AccountStatus.ACTIVE, plainCreated, null, "Cynthia", "Ortiz", LocalDate.of(1990, 4, 25), "210 Sunset Blvd", "Fresno", "CA", "93701", "210 Sunset Blvd");
        seedChecking("CH-0000010017", new BigDecimal("7120.00"), AccountStatus.ACTIVE, plainCreated, null, "Daniel", "Reed", LocalDate.of(1976, 8, 17), "63 Magnolia St", "Tulsa", "OK", "74101", "63 Magnolia St");
        seedChecking("CH-0000010018", new BigDecimal("2450.55"), AccountStatus.ACTIVE, plainCreated, null, "Elena", "Vargas", LocalDate.of(1993, 12, 9), "18 Riverside Dr", "Albuquerque", "NM", "87101", "18 Riverside Dr");
        seedChecking("CH-0000010019", new BigDecimal("5980.30"), AccountStatus.ACTIVE, plainCreated, null, "Felix", "Ward", LocalDate.of(1981, 3, 27), "500 Highland Ave", "Louisville", "KY", "40201", "500 Highland Ave");
        seedChecking("CH-0000010020", new BigDecimal("3140.70"), AccountStatus.ACTIVE, plainCreated, null, "Gina", "Torres", LocalDate.of(1988, 6, 13), "27 Meadow Ln", "Baton Rouge", "LA", "70801", "27 Meadow Ln");
        seedChecking("CH-0000010021", new BigDecimal("6890.45"), AccountStatus.ACTIVE, plainCreated, null, "Hassan", "Ali", LocalDate.of(1992, 10, 22), "140 Grove St", "Richmond", "VA", "23218", "140 Grove St");
        seedChecking("CH-0000010022", new BigDecimal("2670.80"), AccountStatus.ACTIVE, plainCreated, null, "Isabel", "Cruz", LocalDate.of(1987, 1, 19), "9 Harbor Way", "Providence", "RI", "02901", "9 Harbor Way");
        seedChecking("CH-0000010023", new BigDecimal("4980.20"), AccountStatus.ACTIVE, plainCreated, null, "Jerome", "Bell", LocalDate.of(1975, 5, 6), "310 Union St", "Hartford", "CT", "06101", "310 Union St");
        seedChecking("CH-0000010024", new BigDecimal("3760.10"), AccountStatus.ACTIVE, plainCreated, null, "Kayla", "Simmons", LocalDate.of(1995, 9, 2), "72 Fairview Rd", "Madison", "WI", "53701", "72 Fairview Rd");
        seedChecking("CH-0000010025", new BigDecimal("5210.65"), AccountStatus.ACTIVE, plainCreated, null, "Louis", "Fischer", LocalDate.of(1983, 12, 30), "205 Chestnut St", "Des Moines", "IA", "50301", "205 Chestnut St");

        seedSavings("SV-0000020001", new BigDecimal("15200.00"), AccountStatus.ACTIVE, plainCreated, null, "Maria", "Rodriguez", LocalDate.of(1980, 5, 19), "12 Elm St", "Dallas", "TX", "75201", "12 Elm St");
        seedSavings("SV-0000020002", new BigDecimal("8900.50"), AccountStatus.ACTIVE, plainCreated, null, "Noah", "Anderson", LocalDate.of(1992, 10, 3), "88 Broadway", "San Diego", "CA", "92101", "88 Broadway");
        seedSavings("SV-0000020003", new BigDecimal("22000.75"), AccountStatus.ACTIVE, plainCreated, null, "Olivia", "Harris", LocalDate.of(1987, 3, 22), "5 Music Row", "Nashville", "TN", "37201", "5 Music Row");
        seedSavings("SV-0000020004", new BigDecimal("5600.30"), AccountStatus.CLOSED, LocalDate.now().minusMonths(24), LocalDate.now().minusMonths(3), "Peter", "Clark", LocalDate.of(1976, 12, 15), "300 High St", "Columbus", "OH", "43201", "300 High St");
        seedSavings("SV-0000020005", new BigDecimal("13400.00"), AccountStatus.ACTIVE, plainCreated, null, "Quinn", "Lewis", LocalDate.of(1995, 6, 9), "700 Congress Ave", "Austin", "TX", "78702", "700 Congress Ave");
        seedSavings("SV-0000020006", new BigDecimal("9800.60"), AccountStatus.ACTIVE, plainCreated, null, "Rachel", "Walker", LocalDate.of(1983, 8, 27), "40 Trade St", "Charlotte", "NC", "28201", "40 Trade St");
        seedSavings("SV-0000020007", new BigDecimal("30500.00"), AccountStatus.ACTIVE, plainCreated, null, "Samuel", "Young", LocalDate.of(1971, 1, 12), "120 Fremont St", "Las Vegas", "NV", "89101", "120 Fremont St");
        seedSavings("SV-0000020008", new BigDecimal("4200.15"), AccountStatus.ACTIVE, plainCreated, null, "Tina", "Hall", LocalDate.of(1998, 9, 30), "9 Orange Ave", "Orlando", "FL", "32801", "9 Orange Ave");
        seedSavings("SV-0000020009", new BigDecimal("17650.40"), AccountStatus.ACTIVE, plainCreated, null, "Victor", "King", LocalDate.of(1989, 4, 18), "60 Nicollet Mall", "Minneapolis", "MN", "55401", "60 Nicollet Mall");
        seedSavings("SV-0000020010", new BigDecimal("6700.25"), AccountStatus.ACTIVE, plainCreated, null, "Wendy", "Scott", LocalDate.of(1974, 11, 2), "15 Capitol Mall", "Sacramento", "CA", "95814", "15 Capitol Mall");
        seedSavings("SV-0000020011", new BigDecimal("11200.35"), AccountStatus.ACTIVE, plainCreated, null, "Monica", "Diaz", LocalDate.of(1986, 2, 14), "44 Lakeview Dr", "Salt Lake City", "UT", "84101", "44 Lakeview Dr");
        seedSavings("SV-0000020012", new BigDecimal("8650.90"), AccountStatus.ACTIVE, plainCreated, null, "Nathan", "Brooks", LocalDate.of(1990, 6, 28), "77 Ridge Rd", "Little Rock", "AR", "72201", "77 Ridge Rd");
        seedSavings("SV-0000020013", new BigDecimal("19800.00"), AccountStatus.ACTIVE, plainCreated, null, "Priya", "Patel", LocalDate.of(1993, 10, 11), "212 Sunrise Ave", "Anchorage", "AK", "99501", "212 Sunrise Ave");
        seedSavings("SV-0000020014", new BigDecimal("7340.55"), AccountStatus.ACTIVE, plainCreated, null, "Oscar", "Delgado", LocalDate.of(1979, 4, 7), "63 Pinecrest Ln", "Spokane", "WA", "99201", "63 Pinecrest Ln");
        seedSavings("SV-0000020015", new BigDecimal("14500.20"), AccountStatus.ACTIVE, plainCreated, null, "Paula", "Nguyen", LocalDate.of(1996, 8, 23), "18 Bayview Ter", "Honolulu", "HI", "96801", "18 Bayview Ter");
        seedSavings("SV-0000020016", new BigDecimal("9990.10"), AccountStatus.ACTIVE, plainCreated, null, "Ryan", "Mitchell", LocalDate.of(1985, 12, 5), "300 Foothill Blvd", "Reno", "NV", "89501", "300 Foothill Blvd");
        seedSavings("SV-0000020017", new BigDecimal("23100.75"), AccountStatus.ACTIVE, plainCreated, null, "Sofia", "Ramirez", LocalDate.of(1991, 3, 16), "55 Garden St", "Albany", "NY", "12201", "55 Garden St");
        seedSavings("SV-0000020018", new BigDecimal("6420.40"), AccountStatus.ACTIVE, plainCreated, null, "Trevor", "Hughes", LocalDate.of(1977, 7, 1), "140 Maple Ave", "Burlington", "VT", "05401", "140 Maple Ave");
        seedSavings("SV-0000020019", new BigDecimal("16750.00"), AccountStatus.ACTIVE, plainCreated, null, "Ursula", "Bennett", LocalDate.of(1994, 11, 29), "9 Overlook Dr", "Jackson", "MS", "39201", "9 Overlook Dr");
        seedSavings("SV-0000020020", new BigDecimal("10230.85"), AccountStatus.ACTIVE, plainCreated, null, "Vincent", "Nolan", LocalDate.of(1982, 5, 20), "270 Canyon Rd", "Cheyenne", "WY", "82001", "270 Canyon Rd");
        seedSavings("SV-0000020021", new BigDecimal("13890.60"), AccountStatus.ACTIVE, plainCreated, null, "Wanda", "Perry", LocalDate.of(1989, 9, 9), "38 Brookside Ave", "Fargo", "ND", "58102", "38 Brookside Ave");
        seedSavings("SV-0000020022", new BigDecimal("8100.25"), AccountStatus.ACTIVE, plainCreated, null, "Xiomara", "Lopez", LocalDate.of(1997, 1, 26), "115 Southgate Dr", "Wichita", "KS", "67201", "115 Southgate Dr");
        seedSavings("SV-0000020023", new BigDecimal("20450.90"), AccountStatus.ACTIVE, plainCreated, null, "Yusuf", "Ibrahim", LocalDate.of(1984, 6, 4), "6 Lighthouse Rd", "Portland", "ME", "04101", "6 Lighthouse Rd");
        seedSavings("SV-0000020024", new BigDecimal("7560.35"), AccountStatus.ACTIVE, plainCreated, null, "Zoe", "Campbell", LocalDate.of(1992, 10, 18), "82 Timber Ln", "Charleston", "WV", "25301", "82 Timber Ln");
        seedSavings("SV-0000020025", new BigDecimal("12980.50"), AccountStatus.ACTIVE, plainCreated, null, "Aaron", "Blake", LocalDate.of(1980, 2, 27), "29 Windsor Ct", "Manchester", "NH", "03101", "29 Windsor Ct");

        log.info("Seeded {} demo accounts", accountJpaRepository.count());
    }

    private void seedChecking(String accountNumber, BigDecimal balance, AccountStatus status, LocalDate createdDate, LocalDate closedDate,
                               String firstName, String lastName, LocalDate dateOfBirth,
                               String street, String city, String state, String zip, String addressLine1) {
        save(accountNumber, AccountType.CHECKING, balance, status, createdDate, closedDate,
                firstName, lastName, dateOfBirth, street, city, state, zip, addressLine1);
    }

    private void seedSavings(String accountNumber, BigDecimal balance, AccountStatus status, LocalDate createdDate, LocalDate closedDate,
                              String firstName, String lastName, LocalDate dateOfBirth,
                              String street, String city, String state, String zip, String addressLine1) {
        save(accountNumber, AccountType.SAVINGS, balance, status, createdDate, closedDate,
                firstName, lastName, dateOfBirth, street, city, state, zip, addressLine1);
    }

    private void save(String accountNumber, AccountType type, BigDecimal balance, AccountStatus status,
                       LocalDate createdDate, LocalDate closedDate, String firstName, String lastName, LocalDate dateOfBirth,
                       String street, String city, String state, String zip, String addressLine1) {
        CustomerEntity customer = CustomerEntity.builder()
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dateOfBirth)
                .phoneNumber(String.format("512-555-%04d", ++customerSequence))
                .address(AddressEmbeddable.builder()
                        .street(street).city(city).state(state).zip(zip).country("USA").addressLine1(addressLine1)
                        .build())
                .build();
        AccountEntity account = AccountEntity.builder()
                .accountNumber(accountNumber)
                .accountType(type)
                .accountStatus(status)
                .balance(balance)
                .createdDate(createdDate)
                .closedDate(closedDate)
                .customer(customer)
                .build();
        accountJpaRepository.save(account);
    }
}
