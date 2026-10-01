package org.brite.banking.contoller;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.service.EmployeeService;
import org.brite.banking.service.StaffAccountService;
import org.brite.banking.service.StaffLoginService;
import org.brite.banking.domain.Account;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;
import org.brite.banking.exception.AccountClosedException;
import org.brite.banking.exception.AccountNotFoundException;
import org.brite.banking.exception.AccountSuspendedException;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.request.SuspendAccountRequest;
import org.brite.banking.request.UpdateSuspensionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc (no Spring context, no MySQL): binding, validation, status codes and error mapping for suspension on the
 * staff endpoints (suspending, updating a suspension and reactivating are staff-only). The acting employee is the request
 * attribute the staff filter would set; the privilege check itself is covered in StaffControllerTest.
 */
class StaffSuspensionControllerTest {
    private StaffAccountService staff;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        staff = mock(StaffAccountService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StaffController(staff, mock(EmployeeService.class), mock(StaffLoginService.class)))
                .defaultRequest(get("/").requestAttr(StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE, "EMP-M"))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    private Account suspendedAccount() {
        return Account.builder().checkingAccountNumber("CH-0000010001").accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.SUSPENDED).suspended(true)
                .suspendedStart(LocalDateTime.of(2026, 10, 1, 9, 30)).suspendedEnd(LocalDateTime.of(2026, 10, 15, 17, 0))
                .suspensionNotes("Fraud review").build();
    }

    @Test
    void suspendBindsBodyAndReturnsSuspendedAccountJson() throws Exception {
        when(staff.suspend(eq("EMP-M"), eq("CH-0000010001"), any())).thenReturn(suspendedAccount());

        mockMvc.perform(post("/v1/api/staff/accounts/CH-0000010001/suspend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"Fraud review\",\"startDateTime\":\"2026-10-01T09:30:00\",\"endDateTime\":\"2026-10-15T17:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"))
                .andExpect(jsonPath("$.suspended").value(true))
                .andExpect(jsonPath("$.suspendedStart").value("2026-10-01T09:30:00"))
                .andExpect(jsonPath("$.suspendedEnd").value("2026-10-15T17:00:00"))
                .andExpect(jsonPath("$.suspensionNotes").value("Fraud review"));

        ArgumentCaptor<SuspendAccountRequest> request = ArgumentCaptor.forClass(SuspendAccountRequest.class);
        verify(staff).suspend(eq("EMP-M"), eq("CH-0000010001"), request.capture());
        assertEquals(LocalDateTime.of(2026, 10, 15, 17, 0), request.getValue().getEndDateTime());
    }

    @Test
    void suspendWithBlankNotesIsRejectedBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/v1/api/staff/accounts/CH-0000010001/suspend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"  \"}"))
                .andExpect(status().isBadRequest());

        verify(staff, never()).suspend(any(), any(), any());
    }

    @Test
    void suspendWithNotesOver500CharactersIsRejected() throws Exception {
        mockMvc.perform(post("/v1/api/staff/accounts/CH-0000010001/suspend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspendingAnAlreadySuspendedAccountIsPlainText400() throws Exception {
        when(staff.suspend(any(), any(), any())).thenThrow(new AccountSuspendedException("Account CH-0000010001 is already suspended"));

        mockMvc.perform(post("/v1/api/staff/accounts/CH-0000010001/suspend").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"n\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Account CH-0000010001 is already suspended"));
    }

    @Test
    void suspendingAClosedAccountIsPlainText400() throws Exception {
        when(staff.suspend(any(), any(), any())).thenThrow(new AccountClosedException("Account CH-0000010004 is closed and cannot be suspended"));

        mockMvc.perform(post("/v1/api/staff/accounts/CH-0000010004/suspend").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"n\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Account CH-0000010004 is closed and cannot be suspended"));
    }

    @Test
    void suspendingAnUnknownAccountIs404() throws Exception {
        when(staff.suspend(any(), any(), any())).thenThrow(new AccountNotFoundException("Account not found: CH-1"));

        mockMvc.perform(post("/v1/api/staff/accounts/CH-1/suspend").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"n\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void patchSuspensionBindsPartialBody() throws Exception {
        when(staff.updateSuspension(eq("EMP-M"), eq("CH-0000010001"), any())).thenReturn(suspendedAccount());

        mockMvc.perform(patch("/v1/api/staff/accounts/CH-0000010001/suspension").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDateTime\":\"2026-11-01T00:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.suspensionNotes").value("Fraud review"));

        ArgumentCaptor<UpdateSuspensionRequest> request = ArgumentCaptor.forClass(UpdateSuspensionRequest.class);
        verify(staff).updateSuspension(eq("EMP-M"), eq("CH-0000010001"), request.capture());
        assertEquals(LocalDateTime.of(2026, 11, 1, 0, 0), request.getValue().getEndDateTime());
        assertEquals(null, request.getValue().getNotes());
    }

    @Test
    void patchSuspensionOnANotSuspendedAccountIsPlainText400() throws Exception {
        when(staff.updateSuspension(any(), any(), any())).thenThrow(new IllegalArgumentException("Account CH-0000010001 is not suspended"));

        mockMvc.perform(patch("/v1/api/staff/accounts/CH-0000010001/suspension").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"n\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Account CH-0000010001 is not suspended"));
    }

    @Test
    void reactivateReturnsTheActiveAccount() throws Exception {
        Account active = Account.builder().checkingAccountNumber("CH-0000010001").accountStatus(AccountStatus.ACTIVE).build();
        when(staff.reactivate("EMP-M", "CH-0000010001")).thenReturn(active);

        mockMvc.perform(post("/v1/api/staff/accounts/CH-0000010001/reactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.suspended").value(false));
    }
}
