package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

/**
 * A bank employee's user profile. Tellers and managers belong to a branch
 * ({@code bankLocationId}); area managers have none and cover a {@code region}.
 * {@code supervisorId} is the employee this one reports to.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "employeeNumber")
public class Employee {
    private Long id;
    private String employeeNumber;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
    private EmployeeRole role;
    private String jobTitle;
    private EmployeeStatus status;
    private LocalDate hireDate;
    private Long bankLocationId;
    private String region;
    private Long supervisorId;

    /** What this employee may do, derived from {@link #role}. */
    public Set<EmployeePrivilege> getPrivileges() {
        return role.getPrivileges();
    }

    public boolean hasPrivilege(EmployeePrivilege privilege) {
        return status == EmployeeStatus.ACTIVE && role.hasPrivilege(privilege);
    }
}
