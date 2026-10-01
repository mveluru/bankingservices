package org.brite.banking.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.brite.banking.domain.BankOperationServices;
import org.brite.banking.domain.LocationType;

import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;

/** Persistent form of {@link org.brite.banking.domain.BankLocations}; one row per office/ATM. */
@Entity
@Table(name = "bank_locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankLocationEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LocationType locationType;

    @Embedded
    private BankAddressEmbeddable address;

    /** Office hours; null for ATM-only locations. */
    private LocalTime opensAt;
    private LocalTime closesAt;

    @Column(nullable = false, length = 40)
    @Builder.Default
    private String timeZone = "America/Chicago";

    @Column(length = 20)
    private String phoneNumber;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "bank_location_services", joinColumns = @JoinColumn(name = "bank_location_id"))
    @Column(name = "service", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<BankOperationServices> services = new LinkedHashSet<>();
}
