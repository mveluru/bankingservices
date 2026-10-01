package org.brite.banking.service;

import org.brite.banking.domain.BankLocations;
import org.brite.banking.exception.LocationNotFoundException;
import org.brite.banking.repository.LocationBasedOperationRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocationBasedOperationServiceTest {
    private final LocationBasedOperationRepository repository = mock(LocationBasedOperationRepository.class);
    private final LocationBasedOperationService service = new LocationBasedOperationService(repository);

    @Test
    void getLocationReturnsTheLocationWhenFound() {
        BankLocations location = BankLocations.builder().id(7L).name("Chicago O'Hare Airport ATM").build();
        when(repository.findById(7L)).thenReturn(Optional.of(location));

        assertSame(location, service.getLocation(7L));
    }

    @Test
    void getLocationThrowsLocationNotFoundWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        LocationNotFoundException ex = assertThrows(LocationNotFoundException.class, () -> service.getLocation(99L));
        assertEquals("Bank location not found: 99", ex.getMessage());
    }
}
