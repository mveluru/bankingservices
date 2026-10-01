package org.brite.banking.service;

import lombok.RequiredArgsConstructor;
import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;
import org.brite.banking.exception.EmployeeNotAuthorizedException;
import org.brite.banking.exception.EmployeeNotFoundException;
import org.brite.banking.messages.BankingMessages;
import org.brite.banking.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Set;

/**
 * Decides what an employee may do. The acting employee is identified by the
 * {@value #EMPLOYEE_HEADER} header; like {@code X-Customer-Id} that is a claimed identity, not
 * authentication, so this enforces the role model but can't prove who is calling.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {
    public static final String EMPLOYEE_HEADER = "X-Employee-Number";
    private static final Set<String> SORTABLE = Set.of("lastName", "firstName", "employeeNumber", "role", "hireDate");

    private final EmployeeRepository employeeRepository;

    /**
     * Returns the acting employee if they are ACTIVE and their role grants {@code privilege}.
     *
     * @throws IllegalArgumentException if the header is missing or blank (mapped to 400)
     * @throws EmployeeNotFoundException if no such employee exists (mapped to 404)
     * @throws EmployeeNotAuthorizedException if inactive or lacking the privilege (mapped to 403)
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
            throw new IllegalArgumentException(String.format(BankingMessages.EMPLOYEE_HEADER_REQUIRED, EMPLOYEE_HEADER));
        }
        Employee employee = employeeRepository.findByEmployeeNumber(employeeNumber.trim())
                .orElseThrow(() -> new EmployeeNotFoundException(String.format(BankingMessages.EMPLOYEE_NOT_FOUND, employeeNumber.trim())));
        if (employee.getStatus() != EmployeeStatus.ACTIVE) {
            throw new EmployeeNotAuthorizedException(String.format(
                    BankingMessages.EMPLOYEE_NOT_ACTIVE, employee.getEmployeeNumber(), employee.getStatus()));
        }
        return employee;
    }
}
