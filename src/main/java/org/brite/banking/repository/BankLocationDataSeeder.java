package org.brite.banking.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.BankOperationServices;
import org.brite.banking.domain.LocationType;
import org.brite.banking.entity.BankAddressEmbeddable;
import org.brite.banking.entity.BankLocationEntity;
import org.brite.banking.repository.jpa.BankLocationJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;

import static org.brite.banking.domain.BankOperationServices.*;

/**
 * Seeds 20 demo bank locations (8 OFFICE, 6 ATM, 6 BOTH) across Central-time cities, but only
 * if the bank_locations table is empty - same run-once rule as {@link AccountDataSeeder}.
 * Offices/BOTH are open 8 AM - 4 PM Central ("America/Chicago", so daylight saving is handled)
 * with an office phone; ATM-only locations have no hours or phone. Phone numbers use the
 * reserved fictional 555-01xx range.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class BankLocationDataSeeder {
    private static final LocalTime OPENS_AT = LocalTime.of(8, 0);
    private static final LocalTime CLOSES_AT = LocalTime.of(16, 0);
    private static final String TIME_ZONE = "America/Chicago";

    private final BankLocationJpaRepository bankLocationJpaRepository;

    @PostConstruct
    @Transactional
    public void seedIfEmpty() {
        if (bankLocationJpaRepository.count() > 0) {
            log.info("Bank locations table already has data; skipping demo seed");
            return;
        }
        log.info("Seeding demo bank locations");

        bankLocationJpaRepository.saveAll(List.of(
                seed("Austin Downtown Branch", LocationType.OFFICE, "300 Congress Ave", null, "Austin", "TX", "78701", "(512) 555-0101", BANKING, LOANS_MORTGAGES, NOTARY),
                seed("Dallas Main Street Branch", LocationType.OFFICE, "1500 Main St", "Suite 100", "Dallas", "TX", "75201", "(214) 555-0102", BANKING, SAFE_DEPOSIT_LOCKER, WIRE_TRANSFER),
                seed("Houston Galleria Branch", LocationType.BOTH, "5085 Westheimer Rd", null, "Houston", "TX", "77056", "(713) 555-0103", BANKING, SAFE_DEPOSIT_LOCKER, FOREIGN_EXCHANGE, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("San Antonio Riverwalk ATM", LocationType.ATM, "100 E Commerce St", null, "San Antonio", "TX", "78205", null, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("Fort Worth Sundance Square Branch", LocationType.OFFICE, "420 Main St", null, "Fort Worth", "TX", "76102", "(817) 555-0105", BANKING, LOANS_MORTGAGES),
                seed("Chicago Loop Branch", LocationType.BOTH, "10 S LaSalle St", null, "Chicago", "IL", "60603", "(312) 555-0106", BANKING, SAFE_DEPOSIT_LOCKER, WIRE_TRANSFER, FOREIGN_EXCHANGE, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("Chicago O'Hare Airport ATM", LocationType.ATM, "10000 W O'Hare Ave", "Terminal 1", "Chicago", "IL", "60666", null, ATM_CASH_WITHDRAWAL),
                seed("Milwaukee Wisconsin Ave Branch", LocationType.OFFICE, "500 W Wisconsin Ave", null, "Milwaukee", "WI", "53203", "(414) 555-0108", BANKING, NOTARY, LOANS_MORTGAGES),
                seed("Madison Capitol Square ATM", LocationType.ATM, "1 E Main St", null, "Madison", "WI", "53703", null, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("Minneapolis Nicollet Branch", LocationType.BOTH, "800 Nicollet Mall", null, "Minneapolis", "MN", "55402", "(612) 555-0110", BANKING, SAFE_DEPOSIT_LOCKER, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("Kansas City Plaza Branch", LocationType.OFFICE, "4600 J C Nichols Pkwy", null, "Kansas City", "MO", "64112", "(816) 555-0111", BANKING, SAFE_DEPOSIT_LOCKER, NOTARY),
                seed("St. Louis Gateway Branch", LocationType.BOTH, "1 N Broadway", null, "St. Louis", "MO", "63102", "(314) 555-0112", BANKING, WIRE_TRANSFER, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("Nashville Music Row Branch", LocationType.OFFICE, "1200 Demonbreun St", null, "Nashville", "TN", "37203", "(615) 555-0113", BANKING, LOANS_MORTGAGES, NOTARY),
                seed("Memphis Beale Street ATM", LocationType.ATM, "150 Beale St", null, "Memphis", "TN", "38103", null, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("New Orleans Canal Street Branch", LocationType.BOTH, "365 Canal St", null, "New Orleans", "LA", "70130", "(504) 555-0115", BANKING, SAFE_DEPOSIT_LOCKER, FOREIGN_EXCHANGE, ATM_CASH_WITHDRAWAL),
                seed("Oklahoma City Bricktown Branch", LocationType.OFFICE, "100 E Sheridan Ave", null, "Oklahoma City", "OK", "73104", "(405) 555-0116", BANKING, LOANS_MORTGAGES),
                seed("Omaha Old Market ATM", LocationType.ATM, "1100 Howard St", null, "Omaha", "NE", "68102", null, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("Des Moines Ingersoll Branch", LocationType.BOTH, "2900 Ingersoll Ave", null, "Des Moines", "IA", "50312", "(515) 555-0118", BANKING, NOTARY, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT),
                seed("Birmingham Five Points Branch", LocationType.OFFICE, "2000 Highland Ave", null, "Birmingham", "AL", "35205", "(205) 555-0119", BANKING, SAFE_DEPOSIT_LOCKER, LOANS_MORTGAGES),
                seed("Little Rock River Market ATM", LocationType.ATM, "400 President Clinton Ave", null, "Little Rock", "AR", "72201", null, ATM_CASH_WITHDRAWAL, ATM_DEPOSIT)
        ));
    }

    private BankLocationEntity seed(String name, LocationType type, String addressLine1, String addressLine2,
                                    String city, String state, String zip, String phone,
                                    BankOperationServices... services) {
        boolean hasOffice = type != LocationType.ATM;
        return BankLocationEntity.builder()
                .name(name)
                .locationType(type)
                .address(BankAddressEmbeddable.builder()
                        .addressLine1(addressLine1).addressLine2(addressLine2)
                        .city(city).state(state).zip(zip).build())
                .opensAt(hasOffice ? OPENS_AT : null)
                .closesAt(hasOffice ? CLOSES_AT : null)
                .timeZone(TIME_ZONE)
                .phoneNumber(phone)
                .services(new LinkedHashSet<>(List.of(services)))
                .build();
    }
}
