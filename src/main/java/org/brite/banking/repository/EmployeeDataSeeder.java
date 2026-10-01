package org.brite.banking.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.entity.BankLocationEntity;
import org.brite.banking.entity.EmployeeEntity;
import org.brite.banking.repository.jpa.BankLocationJpaRepository;
import org.brite.banking.repository.jpa.EmployeeJpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Seeds 21 demo employees (3 area managers, 6 branch managers, 12 tellers), but only if the
 * bank_employees table is empty - same run-once rule as {@link BankLocationDataSeeder}.
 * Branches are looked up by name (ids can drift), so the location seeder is injected purely to
 * force it to run first. Tellers report to their branch manager, or to the area manager where the
 * branch has none; managers report to an area manager. One teller is ON_LEAVE. Names are fictional,
 * emails use the reserved {@code brite-bank.example} domain, phones the fictional 555 exchange.
 * Hire dates are relative to today.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EmployeeDataSeeder {
    private static final String EMAIL_DOMAIN = "brite-bank.example";

    private final EmployeeJpaRepository employeeJpaRepository;
    private final BankLocationJpaRepository bankLocationJpaRepository;
    @SuppressWarnings("unused")
    private final BankLocationDataSeeder bankLocationDataSeeder;

    @PostConstruct
    @Transactional
    public void seedIfEmpty() {
        if (employeeJpaRepository.count() > 0) {
            log.info("Bank employees table already has data; skipping demo seed");
            return;
        }
        log.info("Seeding demo bank employees");

        Map<String, Long> locationIds = new HashMap<>();
        for (BankLocationEntity location : bankLocationJpaRepository.findAll()) {
            locationIds.put(location.getName(), location.getId());
        }

        // Area managers (no branch, cover a region)
        long southwest = save("EMP-000001", "Priya", "Raman", "512-555-0201", EmployeeRole.AREA_MANAGER, "Area Manager, Southwest", EmployeeStatus.ACTIVE, 60, null, "Southwest", null);
        long midwest = save("EMP-000002", "Daniel", "Okafor", "312-555-0202", EmployeeRole.AREA_MANAGER, "Area Manager, Midwest", EmployeeStatus.ACTIVE, 54, null, "Midwest", null);
        long southCentral = save("EMP-000003", "Elena", "Vasquez", "816-555-0203", EmployeeRole.AREA_MANAGER, "Area Manager, South Central", EmployeeStatus.ACTIVE, 48, null, "South Central", null);

        // Branch managers
        long austinMgr = save("EMP-000004", "Marcus", "Bell", "512-555-0204", EmployeeRole.MANAGER, "Branch Manager", EmployeeStatus.ACTIVE, 40, locationIds.get("Austin Downtown Branch"), null, southwest);
        long houstonMgr = save("EMP-000005", "Hannah", "Kim", "713-555-0205", EmployeeRole.MANAGER, "Branch Manager", EmployeeStatus.ACTIVE, 36, locationIds.get("Houston Galleria Branch"), null, southwest);
        long chicagoMgr = save("EMP-000006", "Robert", "Lindqvist", "312-555-0206", EmployeeRole.MANAGER, "Branch Manager", EmployeeStatus.ACTIVE, 44, locationIds.get("Chicago Loop Branch"), null, midwest);
        long minneapolisMgr = save("EMP-000007", "Aisha", "Rahman", "612-555-0207", EmployeeRole.MANAGER, "Branch Manager", EmployeeStatus.ACTIVE, 30, locationIds.get("Minneapolis Nicollet Branch"), null, midwest);
        long kansasCityMgr = save("EMP-000008", "Thomas", "Greene", "816-555-0208", EmployeeRole.MANAGER, "Branch Manager", EmployeeStatus.ACTIVE, 38, locationIds.get("Kansas City Plaza Branch"), null, southCentral);
        long nashvilleMgr = save("EMP-000009", "Sofia", "Marino", "615-555-0209", EmployeeRole.MANAGER, "Branch Manager", EmployeeStatus.ACTIVE, 28, locationIds.get("Nashville Music Row Branch"), null, southCentral);

        // Tellers
        save("EMP-000010", "Lucas", "Meyer", "512-555-0210", EmployeeRole.TELLER, "Senior Teller", EmployeeStatus.ACTIVE, 26, locationIds.get("Austin Downtown Branch"), null, austinMgr);
        save("EMP-000011", "Olivia", "Chen", "512-555-0211", EmployeeRole.TELLER, "Teller", EmployeeStatus.ACTIVE, 9, locationIds.get("Austin Downtown Branch"), null, austinMgr);
        save("EMP-000012", "Jamal", "Carter", "214-555-0212", EmployeeRole.TELLER, "Teller", EmployeeStatus.ACTIVE, 14, locationIds.get("Dallas Main Street Branch"), null, southwest);
        save("EMP-000013", "Emma", "Davis", "713-555-0213", EmployeeRole.TELLER, "Senior Teller", EmployeeStatus.ACTIVE, 22, locationIds.get("Houston Galleria Branch"), null, houstonMgr);
        save("EMP-000014", "Noah", "Patel", "713-555-0214", EmployeeRole.TELLER, "Teller", EmployeeStatus.ACTIVE, 6, locationIds.get("Houston Galleria Branch"), null, houstonMgr);
        save("EMP-000015", "Grace", "Nowak", "312-555-0215", EmployeeRole.TELLER, "Senior Teller", EmployeeStatus.ACTIVE, 31, locationIds.get("Chicago Loop Branch"), null, chicagoMgr);
        save("EMP-000016", "Ethan", "Brooks", "312-555-0216", EmployeeRole.TELLER, "Teller", EmployeeStatus.ON_LEAVE, 12, locationIds.get("Chicago Loop Branch"), null, chicagoMgr);
        save("EMP-000017", "Mia", "Johansson", "612-555-0217", EmployeeRole.TELLER, "Teller", EmployeeStatus.ACTIVE, 8, locationIds.get("Minneapolis Nicollet Branch"), null, minneapolisMgr);
        save("EMP-000018", "Carlos", "Ortega", "816-555-0218", EmployeeRole.TELLER, "Teller", EmployeeStatus.ACTIVE, 17, locationIds.get("Kansas City Plaza Branch"), null, kansasCityMgr);
        save("EMP-000019", "Layla", "Hassan", "615-555-0219", EmployeeRole.TELLER, "Teller", EmployeeStatus.ACTIVE, 4, locationIds.get("Nashville Music Row Branch"), null, nashvilleMgr);
        save("EMP-000020", "Ben", "Whitaker", "414-555-0220", EmployeeRole.TELLER, "Teller", EmployeeStatus.ACTIVE, 11, locationIds.get("Milwaukee Wisconsin Ave Branch"), null, midwest);
        save("EMP-000021", "Chloe", "Dubois", "504-555-0221", EmployeeRole.TELLER, "Senior Teller", EmployeeStatus.ACTIVE, 24, locationIds.get("New Orleans Canal Street Branch"), null, southCentral);
    }

    private long save(String employeeNumber, String firstName, String lastName, String phone, EmployeeRole role,
                      String jobTitle, EmployeeStatus status, int monthsEmployed, Long bankLocationId,
                      String region, Long supervisorId) {
        return employeeJpaRepository.save(EmployeeEntity.builder()
                .employeeNumber(employeeNumber)
                .firstName(firstName)
                .lastName(lastName)
                .email((firstName + "." + lastName).toLowerCase() + "@" + EMAIL_DOMAIN)
                .phoneNumber(phone)
                .role(role)
                .jobTitle(jobTitle)
                .status(status)
                .hireDate(LocalDate.now().minusMonths(monthsEmployed))
                .bankLocationId(bankLocationId)
                .region(region)
                .supervisorId(supervisorId)
                .build()).getId();
    }
}
