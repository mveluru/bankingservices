package org.brite.banking.contoller;

import org.brite.banking.domain.BankAddress;
import org.brite.banking.domain.BankLocations;
import org.brite.banking.domain.BankOperationServices;
import org.brite.banking.domain.LocationType;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.LocationNotFoundException;
import org.brite.banking.service.LocationBasedOperationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc (no Spring context, so no MySQL) - checks param binding and JSON shape. */
class BankLocationControllerTest {
    private LocationBasedOperationService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(LocationBasedOperationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BankLocationController(service))
                .setControllerAdvice(new BankingExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                // Mirror Spring Boot's Jackson setup: java.time module on, ISO strings not timestamps.
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void bindsFiltersAndPagingAndReturnsLocationJson() throws Exception {
        BankLocations austin = BankLocations.builder()
                .id(1L).name("Austin Downtown Branch").locationType(LocationType.OFFICE)
                .bankAddress(BankAddress.builder().addressLine1("300 Congress Ave").city("Austin").state("TX").zip("78701").build())
                .opensAt(LocalTime.of(8, 0)).closesAt(LocalTime.of(16, 0)).phoneNumber("(512) 555-0101")
                .services(Set.of(BankOperationServices.BANKING)).build();
        when(service.listLocations(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(austin), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v1/api/locations")
                        .param("type", "OFFICE").param("state", "TX").param("service", "BANKING")
                        .param("page", "1").param("size", "5").param("sort", "city,desc"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.content[0].name").value("Austin Downtown Branch"))
                .andExpect(jsonPath("$.content[0].locationType").value("OFFICE"))
                .andExpect(jsonPath("$.content[0].bankAddress.city").value("Austin"))
                .andExpect(jsonPath("$.content[0].opensAt").value("08:00:00"))
                .andExpect(jsonPath("$.content[0].phoneNumber").value("(512) 555-0101"));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).listLocations(eq(LocationType.OFFICE), eq(null), eq("TX"), eq(null),
                eq(BankOperationServices.BANKING), pageable.capture());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(5, pageable.getValue().getPageSize());
        assertEquals("city: DESC", pageable.getValue().getSort().toString());
    }

    @Test
    void appliesDefaultPageSizeAndNameSort() throws Exception {
        when(service.listLocations(any(), any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/v1/api/locations")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).listLocations(eq(null), eq(null), eq(null), eq(null), eq(null), pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
        assertEquals("name: ASC", pageable.getValue().getSort().toString());
    }

    @Test
    void invalidSortIsReturnedAsBadRequestText() throws Exception {
        when(service.listLocations(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Unsupported sort property: phoneNumber"));

        mockMvc.perform(get("/v1/api/locations").param("sort", "phoneNumber"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Unsupported sort property: phoneNumber"));
    }

    @Test
    void unknownTypeValueIsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/api/locations").param("type", "KIOSK")).andExpect(status().isBadRequest());
    }

    @Test
    void getByIdReturnsTheLocation() throws Exception {
        BankLocations atm = BankLocations.builder().id(4L).name("San Antonio Riverwalk ATM")
                .locationType(LocationType.ATM).services(Set.of(BankOperationServices.ATM_DEPOSIT)).build();
        when(service.getLocation(4L)).thenReturn(atm);

        mockMvc.perform(get("/v1/api/locations/4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(4))
                .andExpect(jsonPath("$.name").value("San Antonio Riverwalk ATM"))
                .andExpect(jsonPath("$.opensAt").doesNotExist());
    }

    @Test
    void getByIdReturns404TextWhenMissing() throws Exception {
        when(service.getLocation(99L)).thenThrow(new LocationNotFoundException("Bank location not found: 99"));

        mockMvc.perform(get("/v1/api/locations/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Bank location not found: 99"));
    }

    @Test
    void getByIdWithNonNumericIdIsBadRequest() throws Exception {
        mockMvc.perform(get("/v1/api/locations/abc")).andExpect(status().isBadRequest());
    }
}
