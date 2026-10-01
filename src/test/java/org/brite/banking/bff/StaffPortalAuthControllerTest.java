package org.brite.banking.bff;

import com.fasterxml.jackson.databind.SerializationFeature;
import org.brite.banking.bff.controller.StaffPortalAuthController;
import org.brite.banking.bff.dto.PortalEmployee;
import org.brite.banking.bff.dto.StaffPortalLoginResponse;
import org.brite.banking.bff.service.StaffPortalAuthService;
import org.brite.banking.bff.service.StaffPortalService;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.domain.LoginStatusView;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.domain.SecurityQuestionView;
import org.brite.banking.exception.BankingExceptionHandler;
import org.brite.banking.exception.EmployeeLockedException;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.InvalidCredentialsException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.gateway.StaffAuthenticationFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StaffPortalAuthControllerTest {
    private static final String ANSWERS = "[{\"question\":\"FIRST_CAR\",\"answer\":\"Honda Civic\"},{\"question\":\"FIRST_SCHOOL\",\"answer\":\"Oak Street\"},"
            + "{\"question\":\"FIRST_TEACHER\",\"answer\":\"Mrs Patel\"}]";
    private static final String ATTR = StaffAuthenticationFilter.EMPLOYEE_ATTRIBUTE;

    private StaffPortalAuthService service;
    private StaffPortalService staffPortalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(StaffPortalAuthService.class);
        staffPortalService = mock(StaffPortalService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StaffPortalAuthController(service, staffPortalService))
                .setControllerAdvice(new BankingExceptionHandler())
                .setMessageConverters(new StringHttpMessageConverter(), new MappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder.json()
                        .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).build()))
                .build();
    }

    @Test
    void loginReturnsTheTokenEmployeeAndBranchWithNoStoreAndNoPassword() throws Exception {
        PortalEmployee card = PortalEmployee.of(Employee.builder().employeeNumber("EMP-000010").firstName("Lucas").lastName("Meyer")
                .role(EmployeeRole.TELLER).status(EmployeeStatus.ACTIVE).build());
        when(service.login("lucas.meyer", "20260010")).thenReturn(new StaffPortalLoginResponse("signed.jwt", "Bearer", 1800, card, null));

        mockMvc.perform(post("/bff/v1/staff/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"lucas.meyer\",\"password\":\"20260010\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.accessToken").value("signed.jwt"))
                .andExpect(jsonPath("$.employee.employeeNumber").value("EMP-000010"))
                .andExpect(jsonPath("$.employee.privileges.length()").value(4))
                .andExpect(content().string(not(containsString("20260010"))));
    }

    @Test
    void loginFailuresMapTo401423And403AndAMissingFieldIs400() throws Exception {
        when(service.login(eq("bad"), any())).thenThrow(new InvalidCredentialsException("Invalid username or password"));
        when(service.login(eq("locked"), any())).thenThrow(new EmployeeLockedException("Too many failed login attempts; locked until x"));
        when(service.login(eq("sus"), any())).thenThrow(new LoginNotActiveException("Employee EMP-S login is SUSPENDED; only an ACTIVE login can perform transactions"));
        for (String[] c : new String[][]{{"bad", "401"}, {"locked", "423"}, {"sus", "403"}}) {
            mockMvc.perform(post("/bff/v1/staff/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"" + c[0] + "\",\"password\":\"20260010\"}"))
                    .andExpect(status().is(Integer.parseInt(c[1])));
        }
        mockMvc.perform(post("/bff/v1/staff/login").contentType(MediaType.APPLICATION_JSON).content("{\"password\":\"20260010\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void changePasswordAndQuestionsUseTheEmployeeFromTheTokenAttribute() throws Exception {
        when(service.setSecurityQuestions(eq("EMP-000010"), any())).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_CAR)));

        mockMvc.perform(put("/bff/v1/staff/password").requestAttr(ATTR, "EMP-000010").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"20260010\",\"newPassword\":\"13572468\"}")).andExpect(status().isNoContent());
        verify(service).changePassword(eq("EMP-000010"), any());
        mockMvc.perform(put("/bff/v1/staff/security-questions").requestAttr(ATTR, "EMP-000010").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"20260010\",\"answers\":" + ANSWERS + "}"))
                .andExpect(status().isOk()).andExpect(content().string(not(containsString("Honda"))));
    }

    @Test
    void theCatalogAndTheResetCallsAreRoutedAndValidated() throws Exception {
        when(service.questionCatalog()).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_CAR)));
        when(service.resetQuestions("lucas.meyer")).thenReturn(List.of(SecurityQuestionView.of(SecurityQuestion.FIRST_SCHOOL)));

        mockMvc.perform(get("/bff/v1/staff/security-questions/catalog")).andExpect(status().isOk()).andExpect(jsonPath("$[0].question").value("FIRST_CAR"));
        mockMvc.perform(post("/bff/v1/staff/password-reset/questions").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"lucas.meyer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].question").value("FIRST_SCHOOL"));
        mockMvc.perform(post("/bff/v1/staff/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"lucas.meyer\",\"newPassword\":\"13572468\",\"answers\":" + ANSWERS + "}")).andExpect(status().isNoContent());
        verify(service).resetPassword(any());
        mockMvc.perform(post("/bff/v1/staff/password-reset").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"lucas.meyer\",\"newPassword\":\"13572468\",\"answers\":[]}")).andExpect(status().isBadRequest());
    }

    @Test
    void anAreaManagerSetsAnEmployeesLoginStatusAndPasswordThroughTheStaffPortalService() throws Exception {
        when(staffPortalService.changeEmployeeLoginStatus(eq("EMP-A"), eq("EMP-000010"), any()))
                .thenReturn(LoginStatusView.builder().username("lucas.meyer").status(LoginStatus.SUSPENDED).build());

        mockMvc.perform(put("/bff/v1/staff/employees/EMP-000010/login-status").requestAttr(ATTR, "EMP-A").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        mockMvc.perform(put("/bff/v1/staff/employees/EMP-000010/password").requestAttr(ATTR, "EMP-A").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"24681357\"}"))
                .andExpect(status().isNoContent()).andExpect(content().string(""));
        verify(staffPortalService).setEmployeePassword(eq("EMP-A"), eq("EMP-000010"), any());
    }

    @Test
    void employeeAdminCallsMapPrivilegeFailuresTo403AndBadBodiesTo400BeforeTheService() throws Exception {
        doThrow(new EmployeeNotAuthorizedException("Employee EMP-M (MANAGER) is not authorized: requires MANAGE_EMPLOYEES"))
                .when(staffPortalService).setEmployeePassword(eq("EMP-M"), any(), any());

        mockMvc.perform(put("/bff/v1/staff/employees/EMP-000010/password").requestAttr(ATTR, "EMP-M").contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"24681357\"}")).andExpect(status().isForbidden());
        mockMvc.perform(put("/bff/v1/staff/employees/EMP-000010/password").requestAttr(ATTR, "EMP-A").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(put("/bff/v1/staff/employees/EMP-000010/login-status").requestAttr(ATTR, "EMP-A").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        // only the forbidden call reached the service; the two malformed bodies were rejected before it
        verify(staffPortalService, times(1)).setEmployeePassword(any(), any(), any());
        verify(staffPortalService, never()).changeEmployeeLoginStatus(any(), any(), any());
    }
}
