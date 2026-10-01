package org.brite.banking.bff;

import org.brite.banking.bff.service.CustomerPasswordPortalService;
import org.brite.banking.domain.CredentialOwnerType;
import org.brite.banking.domain.SecurityQuestion;
import org.brite.banking.request.AdminSetPasswordRequest;
import org.brite.banking.request.ChangePasswordRequest;
import org.brite.banking.request.PasswordResetRequest;
import org.brite.banking.request.SecurityAnswerRequest;
import org.brite.banking.request.SetSecurityQuestionsRequest;
import org.brite.banking.service.CustomerCredentialService;
import org.brite.banking.service.PasswordResetService;
import org.brite.banking.service.StaffLoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** Plain unit test: the customer-password BFF service only delegates to the banking services that own the rules. */
class CustomerPasswordPortalServiceTest {
    private CustomerCredentialService customers;
    private PasswordResetService resets;
    private StaffLoginService staff;
    private CustomerPasswordPortalService service;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerCredentialService.class);
        resets = mock(PasswordResetService.class);
        staff = mock(StaffLoginService.class);
        service = new CustomerPasswordPortalService(customers, resets, staff);
    }

    @Test
    void changePasswordAndSecurityQuestionsDelegateWithTheCustomerIdFromTheToken() {
        service.changePassword(5L, new ChangePasswordRequest("20260005", "13572468"));
        verify(customers).changePassword(5L, "20260005", "13572468");

        List<SecurityAnswerRequest> answers = List.of(new SecurityAnswerRequest(SecurityQuestion.FIRST_CAR, "a1"),
                new SecurityAnswerRequest(SecurityQuestion.FIRST_SCHOOL, "b1"), new SecurityAnswerRequest(SecurityQuestion.FIRST_TEACHER, "c1"));
        service.setSecurityQuestions(5L, new SetSecurityQuestionsRequest("20260005", answers));
        verify(resets).setQuestions(CredentialOwnerType.CUSTOMER, "5", "20260005", answers);
    }

    @Test
    void passwordResetUsesTheCustomerFlowAndTheCatalogListsEveryQuestion() {
        List<SecurityAnswerRequest> answers = List.of(new SecurityAnswerRequest(SecurityQuestion.FIRST_CAR, "a1"));
        service.resetPassword(new PasswordResetRequest("alice.smith", answers, "13572468"));
        verify(resets).reset(CredentialOwnerType.CUSTOMER, "alice.smith", answers, "13572468");
        service.resetQuestions("alice.smith");
        verify(resets).questionsFor(CredentialOwnerType.CUSTOMER, "alice.smith");

        var catalog = service.questionCatalog();
        assertEquals(SecurityQuestion.values().length, catalog.size());
        assertEquals("What was your first car?", catalog.get(0).getText());
        verifyNoInteractions(staff);
    }

    @Test
    void aManagerSettingACustomerPasswordGoesThroughTheBankingStaffService() {
        AdminSetPasswordRequest request = new AdminSetPasswordRequest("24681357");
        service.setPassword("EMP-M", 11L, request);
        verify(staff).setCustomerPassword("EMP-M", 11L, request);
    }
}
