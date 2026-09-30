package org.bee.banking.repository;

import lombok.RequiredArgsConstructor;
import org.bee.banking.domain.BankAddress;
import org.bee.banking.domain.BankLocations;
import org.bee.banking.domain.BankOperationServices;
import org.bee.banking.domain.LocationType;
import org.bee.banking.entity.BankAddressEmbeddable;
import org.bee.banking.entity.BankLocationEntity;
import org.bee.banking.messages.BankingMessages;
import org.bee.banking.repository.jpa.BankLocationJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Facade over {@link BankLocationJpaRepository} that maps {@link BankLocationEntity} to the
 * {@link BankLocations} domain object, same pattern as {@link AccountRepository}.
 */
@Repository
@RequiredArgsConstructor
public class LocationBasedOperationRepository {
    private final BankLocationJpaRepository bankLocationJpaRepository;

    /**
     * Filtered, paginated search; every filter is optional and AND'd, and text filters are
     * case-insensitive equality. {@code type} matches by capability, not by exact stored
     * value: OFFICE returns offices and office+ATM branches, ATM returns ATMs and office+ATM
     * branches, BOTH returns only office+ATM branches. Valid sort properties are {@code name},
     * {@code locationType}, {@code city}, {@code state} (checked before the query so an invalid
     * one is the app's own IllegalArgumentException/400, not a raw Hibernate error).
     */
    @Transactional(readOnly = true)
    public Page<BankLocations> search(LocationType type, String city, String state, String zip,
                                      BankOperationServices service, Pageable pageable) {
        Specification<BankLocationEntity> spec = Specification.unrestricted();
        if (type != null) {
            Set<LocationType> matching = switch (type) {
                case OFFICE -> EnumSet.of(LocationType.OFFICE, LocationType.BOTH);
                case ATM -> EnumSet.of(LocationType.ATM, LocationType.BOTH);
                case BOTH -> EnumSet.of(LocationType.BOTH);
            };
            spec = spec.and((root, query, cb) -> root.get("locationType").in(matching));
        }
        if (city != null) {
            spec = spec.and(addressEqualsIgnoreCase("city", city));
        }
        if (state != null) {
            spec = spec.and(addressEqualsIgnoreCase("state", state));
        }
        if (zip != null) {
            spec = spec.and(addressEqualsIgnoreCase("zip", zip));
        }
        if (service != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.join("services"), service));
        }

        Pageable entityPageable = pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), toEntitySort(pageable.getSort()))
                : pageable;
        return bankLocationJpaRepository.findAll(spec, entityPageable).map(this::toDomain);
    }

    @Transactional(readOnly = true)
    public Optional<BankLocations> findById(Long id) {
        return bankLocationJpaRepository.findById(id).map(this::toDomain);
    }

    private Specification<BankLocationEntity> addressEqualsIgnoreCase(String field, String value) {
        return (root, query, cb) -> cb.equal(cb.lower(root.get("address").get(field)), value.toLowerCase());
    }

    /** Validates the API's sort keys and maps city/state onto the embedded address's property paths. */
    private Sort toEntitySort(Sort sort) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : sort) {
            String property = switch (order.getProperty()) {
                case "name", "locationType" -> order.getProperty();
                case "city", "state" -> "address." + order.getProperty();
                default -> throw new IllegalArgumentException(
                        String.format(BankingMessages.UNSUPPORTED_LOCATION_SORT_PROPERTY, order.getProperty()));
            };
            orders.add(order.withProperty(property));
        }
        return Sort.by(orders);
    }

    private BankLocations toDomain(BankLocationEntity entity) {
        BankAddressEmbeddable a = entity.getAddress();
        return BankLocations.builder()
                .id(entity.getId())
                .name(entity.getName())
                .bankAddress(a == null ? null : BankAddress.builder()
                        .addressLine1(a.getAddressLine1()).addressLine2(a.getAddressLine2())
                        .city(a.getCity()).state(a.getState()).zip(a.getZip()).country(a.getCountry()).build())
                .locationType(entity.getLocationType())
                .opensAt(entity.getOpensAt())
                .closesAt(entity.getClosesAt())
                .timeZone(entity.getTimeZone())
                .phoneNumber(entity.getPhoneNumber())
                .services(new TreeSet<>(entity.getServices()))
                .build();
    }
}
