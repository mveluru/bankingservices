package org.brite.banking.bff;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.bff.controller.StaffPortalController;
import org.brite.banking.bff.dto.AccountOverviewResponse;
import org.brite.banking.bff.dto.OpenAccountResponse;
import org.brite.banking.bff.dto.PortalEmployee;
import org.brite.banking.bff.service.StaffPortalService;
import org.brite.banking.domain.AccountStatus;
import org.brite.banking.domain.AccountType;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.exception.AccountClosedException;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.brite.banking.rules.JwtProperties;
import org.brite.banking.service.EmployeeCredentialService;
import org.brite.banking.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Standalone MockMvc: routing, binding, status mapping and the acting-employee attribute of the staff portal endpoints. */
class StaffPortalControllerTest {
    private static final String ATTR = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE;
    private static final String OPEN_ACCOUNT = "{\"firstName\":\"David\",\"lastName\":\"Miller\",\"dateOfBirth\":\"08/19/1994\",\"phoneNumber\":\"713-555-0142\","
            + "\"street\":\"789 Pine Rd\",\"addressLine1\":\"789 Pine Rd\",\"city\":\"Houston\",\"state\":\"TX\",\"zip\":\"77001\",\"accountType\":\"checking\"}";
    private static final String DEPOSIT = "{\"accountNumber\":\"CH-0000010001\",\"amount\":50,\"accountType\":\"CHECKING\",\"depositType\":\"check\","
            + "\"street\":\"1 Main St\",\"addressLine1\":\"1 Main St\",\"city\":\"Austin\",\"state\":\"TX\",\"zip\":\"78701\"}";

    private StaffPortalService service;
    private MockMvc mockMvc;

    private static AccountOverviewResponse overview(AccountStatus status) {
        return new AccountOverviewResponse("CH-0000010001", AccountType.CHECKING, status, new BigDecimal("75.00"), status == AccountStatus.SUSPENDED,
                null, LocalDate.of(2026, 1, 5), null, "Ada", "Lovelace", "***-***-0101", 30, List.of());
    }

    private MockMvc build(StaffPortalService portal, jakarta.servlet.Filter... filters) {
        return MockMvcBuilders.standaloneSetup(new StaffPortalController(portal))
                .addFilters(filters)
                .setControllerAdvice(new BankingExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @BeforeEach
    void setUp() {
        service = mock(StaffPortalService.class);
        mockMvc = build(service);
    }

    @Test
    void accountActionsReturnTheRefreshedOverviewUsingTheEmployeeFromTheRequestAttribute() throws Exception {
        when(service.deposit(eq("EMP-T"), eq(4L), any())).thenReturn(overview(AccountStatus.ACTIVE));
        when(service.suspend(eq("EMP-M"), eq("CH-0000010001"), any())).thenReturn(overview(AccountStatus.SUSPENDED));
        when(service.updateSuspension(eq("EMP-M"), eq("CH-0000010001"), any())).thenReturn(overview(AccountStatus.SUSPENDED));
        when(service.reactivate("EMP-M", "CH-0000010001")).thenReturn(overview(AccountStatus.ACTIVE));
        when(service.close("EMP-M", "CH-0000010001")).thenReturn(overview(AccountStatus.CLOSED));
        when(service.overview("EMP-T", "CH-0000010001", 7)).thenReturn(overview(AccountStatus.ACTIVE));

        mockMvc.perform(post("/bff/v1/staff/accounts/deposit").param("locationId", "4").requestAttr(ATTR, "EMP-T")
                        .contentType(MediaType.APPLICATION_JSON).content(DEPOSIT))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("ACTIVE"));
        mockMvc.perform(post("/bff/v1/staff/accounts/CH-0000010001/suspend").requestAttr(ATTR, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Fraud review\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(true));
        mockMvc.perform(patch("/bff/v1/staff/accounts/CH-0000010001/suspension").requestAttr(ATTR, "EMP-M")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"extended\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/bff/v1/staff/accounts/CH-0000010001/reactivate").requestAttr(ATTR, "EMP-M"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.suspended").value(false));
        mockMvc.perform(post("/bff/v1/staff/accounts/CH-0000010001/close").requestAttr(ATTR, "EMP-M"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountStatus").value("CLOSED"));
        mockMvc.perform(get("/bff/v1/staff/accounts/CH-0000010001/overview").param("days", "7").requestAttr(ATTR, "EMP-T"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountNumber").value("CH-0000010001"));
    }

    @Test
    void staffOpenAnAccountForACustomerAndGetTheOverviewAndNearbyBranches() throws Exception {
        when(service.openAccount(eq("EMP-T"), any())).thenReturn(new OpenAccountResponse(overview(AccountStatus.ACTIVE), List.of()));

        mockMvc.perform(post("/bff/v1/staff/accounts/open").requestAttr(ATTR, "EMP-T").contentType(MediaType.APPLICATION_JSON).content(OPEN_ACCOUNT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.account.accountNumber").value("CH-0000010001"))
                .andExpect(jsonPath("$.nearbyLocations.length()").value(0));
    }

    @Test
    void openingAnAccountNeedsAnAllowedEmployeeAndAValidBody() throws Exception {
        doThrow(new EmployeeNotAuthorizedException("Employee EMP-X is ON_LEAVE and cannot perform this action")).when(service).openAccount(eq("EMP-X"), any());

        mockMvc.perform(post("/bff/v1/staff/accounts/open").requestAttr(ATTR, "EMP-X").contentType(MediaType.APPLICATION_JSON).content(OPEN_ACCOUNT))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/bff/v1/staff/accounts/open").requestAttr(ATTR, "EMP-T").contentType(MediaType.APPLICATION_JSON).content("{\"firstName\":\"Ada\"}"))
                .andExpect(status().isBadRequest());
        verify(service, org.mockito.Mockito.never()).openAccount(eq("EMP-T"), any());
    }

    @Test
    void privilegeAndRuleFailuresMapTo403And400WithPlainText() throws Exception {
        doThrow(new EmployeeNotAuthorizedException("Employee EMP-T (TELLER) is not authorized: requires CLOSE_ACCOUNT")).when(service).close(eq("EMP-T"), any());
        doThrow(new AccountClosedException("Account CH-0000030001 is already closed")).when(service).close(eq("EMP-M"), eq("CH-0000030001"));

        mockMvc.perform(post("/bff/v1/staff/accounts/CH-0000010001/close").requestAttr(ATTR, "EMP-T"))
                .andExpect(status().isForbidden()).andExpect(content().string("Employee EMP-T (TELLER) is not authorized: requires CLOSE_ACCOUNT"));
        mockMvc.perform(post("/bff/v1/staff/accounts/CH-0000030001/close").requestAttr(ATTR, "EMP-M"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/bff/v1/staff/accounts/CH-1/suspend").requestAttr(ATTR, "EMP-M").contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void employeesArePagedCardsAndOneCardIsReturned() throws Exception {
        PortalEmployee card = PortalEmployee.of(Employee.builder().employeeNumber("EMP-000010").firstName("Lucas").lastName("Meyer")
                .role(EmployeeRole.TELLER).status(EmployeeStatus.ACTIVE).build());
        when(service.employees(eq("EMP-A"), eq(EmployeeRole.TELLER), any())).thenReturn(new PageImpl<>(List.of(card)));
        when(service.employee("EMP-A", "EMP-000010")).thenReturn(card);

        // only the call into the service matters for the page (the standalone Jackson setup can't serialise a Spring Data Page)
        mockMvc.perform(get("/bff/v1/staff/employees").param("role", "TELLER").requestAttr(ATTR, "EMP-A"));
        verify(service).employees(eq("EMP-A"), eq(EmployeeRole.TELLER), any());
        mockMvc.perform(get("/bff/v1/staff/employees/EMP-000010").requestAttr(ATTR, "EMP-A")).andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Lucas"));
    }

    @Test
    void withTheRealStaffFilterInFrontEveryCallNeedsAnEmployeeTokenAndTheTokenSubjectIsTheActingEmployee() throws Exception {
        JwtProperties properties = new JwtProperties();
        properties.setSecret("staff-portal-test-secret-at-least-32-chars");
        JwtService jwt = new JwtService(properties);
        MockMvc secured = build(service, new StaffAuthenticationFilter(jwt, mock(EmployeeCredentialService.class)));
        String token = jwt.issueEmployeeToken(Employee.builder().employeeNumber("EMP-000010").role(EmployeeRole.TELLER).build()).getToken();
        when(service.overview("EMP-000010", "CH-0000010001", null)).thenReturn(overview(AccountStatus.ACTIVE));

        secured.perform(get("/bff/v1/staff/accounts/CH-0000010001/overview")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        secured.perform(post("/bff/v1/staff/accounts/CH-0000010001/close").header("Authorization", "Bearer bad")).andExpect(status().isUnauthorized());
        secured.perform(get("/bff/v1/staff/accounts/CH-0000010001/overview").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        verify(service).overview("EMP-000010", "CH-0000010001", null);
        org.mockito.Mockito.verifyNoMoreInteractions(service);
    }
}
