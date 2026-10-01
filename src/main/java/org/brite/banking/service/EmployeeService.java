package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeeCredential;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.domain.LoginStatus;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.EmployeeNotFoundException;
import org.brite.banking.exception.InvalidTokenException;
import org.brite.banking.exception.LoginNotActiveException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.EmployeeCredentialRepository;
import org.brite.banking.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Decides what an employee may do. The acting employee is the subject of the verified JWT
 * ({@link org.brite.banking.gateway.StaffAuthenticationFilter}); this reloads their employment status, login status
 * and role on every call, so the token proves who is calling but never grants anything by itself.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {
    private static final Set<String> SORTABLE = Set.of("lastName", "firstName", "employeeNumber", "role", "hireDate");

    private final EmployeeRepository employeeRepository;
    private final EmployeeCredentialRepository credentialRepository;

    /**
     * Returns the acting employee if they are ACTIVE, have an ACTIVE login, and their role grants {@code privilege}.
     *
     * @throws org.brite.banking.exception.InvalidTokenException if there is no authenticated employee (mapped to 401)
     * @throws EmployeeNotFoundException if no such employee exists (mapped to 404)
     * @throws EmployeeNotAuthorizedException if not an ACTIVE employee or lacking the privilege (mapped to 403)
     * @throws LoginNotActiveException if the employee has no login or it isn't ACTIVE (mapped to 403)
     */
    public Employee requirePrivilege(String employeeNumber, EmployeePrivilege privilege) {
        Employee employee = requireActive(employeeNumber);
        if (!employee.hasPrivilege(privilege)) {
            throw new EmployeeNotAuthorizedException(String.format(
                    BankingMessages.EMPLOYEE_NOT_AUTHORIZED, employee.getEmployeeNumber(), employee.getRole(), privilege));
        }
        return employee;
    }

    /** An employee can read their own profile; reading anyone else's needs {@code MANAGE_EMPLOYEES}. */
    public Employee getEmployee(String actingEmployeeNumber, String employeeNumber) {
        Employee acting = requireActive(actingEmployeeNumber);
        if (acting.getEmployeeNumber().equals(employeeNumber)) {
            return acting;
        }
        requirePrivilege(actingEmployeeNumber, EmployeePrivilege.MANAGE_EMPLOYEES);
        return employeeRepository.findByEmployeeNumber(employeeNumber)
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.EMPLOYEE_NOT_FOUND, employeeNumber)));
    }

    /** Lists employees (optionally one role); needs {@code MANAGE_EMPLOYEES}. Sort keys are allow-listed. */
    public Page<Employee> listEmployees(String actingEmployeeNumber, EmployeeRole role, Pageable pageable) {
        requirePrivilege(actingEmployeeNumber, EmployeePrivilege.MANAGE_EMPLOYEES);
        pageable.getSort().forEach(order -> {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new IllegalArgumentException(String.format(
                        BankingMessages.UNSUPPORTED_EMPLOYEE_SORT_PROPERTY, order.getProperty()));
            }
        });
        return employeeRepository.search(role, pageable);
    }

    private Employee requireActive(String employeeNumber) {
        if (employeeNumber == null || employeeNumber.isBlank()) {
            throw new InvalidTokenException(BankingMessages.AUTHENTICATION_REQUIRED);
        }
        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber.trim())
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.EMPLOYEE_NOT_FOUND, employeeNumber.trim())));
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new EmployeeNotAuthorizedException(String.format(
                    BankingMessages.EMPLOYEE_NOT_ACTIVE, employee.getEmployeeNumber(), employee.getStatus()));
        }
        EmployeeCredential credential = credentialRepository.findByEmployeeId(employee.getId())
                .orElseThrow(() -> new LoginNotActiveException(String.format(
                        BankingMessages.EMPLOYEE_HAS_NO_LOGIN, employee.getEmployeeNumber())));
        LoginStatus loginStatus = credential.effectiveStatus(LocalDateTime.now());
        if (loginStatus != LoginStatus.ACTIVE) {
            throw new LoginNotActiveException(String.format(
                    BankingMessages.EMPLOYEE_LOGIN_NOT_ACTIVE, employee.getEmployeeNumber(), loginStatus));
        }
        return employee;
    }
}
