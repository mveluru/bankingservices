package org.brite.banking.repository;

import org.brite.banking.domain.BankLocations;
import org.brite.banking.domain.BankOperationServices;
import org.brite.banking.domain.LocationType;
import org.brite.banking.repository.jpa.BankLocationJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real Specification queries against embedded H2 over the 20 rows BankLocationDataSeeder creates
 * (8 OFFICE, 6 ATM, 6 BOTH) - same minimal-context setup as BankLocationDataSeederTest.
 */
@DataJpaTest
@ContextConfiguration(classes = LocationBasedOperationRepositoryTest.Cfg.class)
// application.yml hardcodes the MySQL dialect; clear it so Hibernate builds H2-compatible DDL.
@TestPropertySource(properties = {
        "spring.jpa.properties.hibernate.dialect=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LocationBasedOperationRepositoryTest {

    @Configuration
    @EnableAutoConfiguration
    @EntityScan("org.brite.banking.entity")
    @EnableJpaRepositories(basePackageClasses = BankLocationJpaRepository.class)
    @Import(BankLocationDataSeeder.class)
    static class Cfg {
    }

    private static final Pageable ALL = PageRequest.of(0, 50, Sort.by("name"));

    @Autowired
    private BankLocationJpaRepository jpaRepository;

    private LocationBasedOperationRepository repository;

    @BeforeEach
    void setUp() {
        repository = new LocationBasedOperationRepository(jpaRepository);
    }

    @Test
    void noFiltersReturnsEveryLocation() {
        assertThat(repository.search(null, null, null, null, null, ALL).getTotalElements()).isEqualTo(20);
    }

    @Test
    void officeTypeAlsoReturnsBranchesWithAtms() {
        Page<BankLocations> page = repository.search(LocationType.OFFICE, null, null, null, null, ALL);
        assertThat(page.getTotalElements()).isEqualTo(14);
        assertThat(page.getContent()).extracting(BankLocations::getLocationType)
                .containsOnly(LocationType.OFFICE, LocationType.BOTH);
    }

    @Test
    void atmTypeAlsoReturnsBranchesWithAtms() {
        Page<BankLocations> page = repository.search(LocationType.ATM, null, null, null, null, ALL);
        assertThat(page.getTotalElements()).isEqualTo(12);
        assertThat(page.getContent()).extracting(BankLocations::getLocationType)
                .containsOnly(LocationType.ATM, LocationType.BOTH);
    }

    @Test
    void bothTypeReturnsOnlyBranchesWithAtms() {
        Page<BankLocations> page = repository.search(LocationType.BOTH, null, null, null, null, ALL);
        assertThat(page.getTotalElements()).isEqualTo(6);
        assertThat(page.getContent()).extracting(BankLocations::getLocationType).containsOnly(LocationType.BOTH);
    }

    @Test
    void cityAndStateFiltersAreCaseInsensitive() {
        assertThat(repository.search(null, "chicago", null, null, null, ALL).getTotalElements()).isEqualTo(2);
        assertThat(repository.search(null, null, "tx", null, null, ALL).getTotalElements()).isEqualTo(5);
    }

    @Test
    void zipFilterMatchesOneLocation() {
        Page<BankLocations> page = repository.search(null, null, null, "60603", null, ALL);
        assertThat(page.getContent()).extracting(BankLocations::getName).containsExactly("Chicago Loop Branch");
    }

    @Test
    void serviceFilterMatchesLocationsOfferingIt() {
        Page<BankLocations> page = repository.search(null, null, null, null, BankOperationServices.SAFE_DEPOSIT_LOCKER, ALL);
        assertThat(page.getTotalElements()).isEqualTo(7);
        assertThat(page.getContent()).allSatisfy(l ->
                assertThat(l.getServices()).contains(BankOperationServices.SAFE_DEPOSIT_LOCKER));
    }

    @Test
    void filtersAreCombinedWithAnd() {
        Page<BankLocations> page = repository.search(LocationType.ATM, null, "TX", null,
                BankOperationServices.ATM_DEPOSIT, ALL);
        assertThat(page.getContent()).extracting(BankLocations::getName)
                .containsExactlyInAnyOrder("Houston Galleria Branch", "San Antonio Riverwalk ATM");
    }

    @Test
    void mapsAddressHoursPhoneAndServicesToTheDomainObject() {
        BankLocations austin = repository.search(null, "Austin", null, null, null, ALL).getContent().get(0);
        assertThat(austin.getBankAddress().getAddressLine1()).isEqualTo("300 Congress Ave");
        assertThat(austin.getBankAddress().getZip()).isEqualTo("78701");
        assertThat(austin.getOpensAt()).isEqualTo(LocalTime.of(8, 0));
        assertThat(austin.getClosesAt()).isEqualTo(LocalTime.of(16, 0));
        assertThat(austin.getTimeZone()).isEqualTo("America/Chicago");
        assertThat(austin.getPhoneNumber()).isEqualTo("(512) 555-0101");
        assertThat(austin.getServices()).containsExactly(BankOperationServices.BANKING,
                BankOperationServices.LOANS_MORTGAGES, BankOperationServices.NOTARY);
    }

    @Test
    void paginatesAndSortsByCityDescending() {
        Page<BankLocations> page = repository.search(null, null, null, null, null,
                PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "city")));
        assertThat(page.getTotalElements()).isEqualTo(20);
        assertThat(page.getContent()).extracting(l -> l.getBankAddress().getCity())
                .containsExactly("St. Louis", "San Antonio", "Omaha");
    }

    @Test
    void unsupportedSortPropertyIsRejected() {
        assertThatThrownBy(() -> repository.search(null, null, null, null, null,
                PageRequest.of(0, 5, Sort.by("phoneNumber"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("phoneNumber");
    }

    @Test
    void findByIdReturnsTheMappedLocation() {
        Long id = repository.search(null, "Austin", null, null, null, ALL).getContent().get(0).getId();
        BankLocations found = repository.findById(id).orElseThrow();
        assertThat(found.getName()).isEqualTo("Austin Downtown Branch");
        assertThat(found.getServices()).contains(BankOperationServices.NOTARY);
    }

    @Test
    void findByIdIsEmptyForUnknownId() {
        assertThat(repository.findById(999_999L)).isEmpty();
    }
}
