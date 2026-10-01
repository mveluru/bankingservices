package org.brite.banking.bff.dto;

import org.brite.banking.domain.Employee;
import org.brite.banking.domain.EmployeePrivilege;
import org.brite.banking.domain.EmployeeRole;
import org.brite.banking.domain.EmployeeStatus;

import java.time.LocalDate;
import java.util.Set;

/**
 * An employee as the staff portal shows them: name, role, what the role allows, where they work. Email, phone, the internal ids and
 * the supervisor link are left out; nothing here is a credential.
 */
public record PortalEmployee(String employeeNumber, String firstName, String lastName, EmployeeRole role, String jobTitle,
                             EmployeeStatus status, LocalDate hireDate, Long bankLocationId, String region,
                             Set<EmployeePrivilege> privileges) {

    public static PortalEmployee of(Employee e) {
        return new PortalEmployee(e.getEmployeeNumber(), e.getFirstName(), e.getLastName(), e.getRole(), e.getJobTitle(),
                e.getStatus(), e.getHireDate(), e.getBankLocationId(), e.getRegion(), e.getPrivileges());
    }
}
