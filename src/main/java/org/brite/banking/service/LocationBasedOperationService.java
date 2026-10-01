package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.brite.banking.domain.BankLocations;
import org.brite.banking.domain.BankOperationServices;
import org.brite.banking.domain.LocationType;
import org.brite.banking.exception.LocationNotFoundException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.LocationBasedOperationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/** Looks up bank offices/ATMs by location, type and the operations they serve. */
@Service
@Slf4j
@RequiredArgsConstructor
public class LocationBasedOperationService {
    private final LocationBasedOperationRepository locationRepository;

    public Page<BankLocations> listLocations(LocationType type, String city, String state, String zip,
                                             BankOperationServices service, Pageable pageable) {
        log.debug(BankingMessages.LOG_LOCATION_SEARCH, type, city, state, zip, service, pageable.getPageNumber());
        return locationRepository.search(type, city, state, zip, service, pageable);
    }

    /** @throws LocationNotFoundException (mapped to 404) if no location has this id */
    public BankLocations getLocation(Long id) {
        return locationRepository.findById(id)
                .orElseThrow(() -> new LocationNotFoundException(String.format(BankingMessages.LOCATION_NOT_FOUND, id)));
    }
}
