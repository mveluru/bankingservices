package org.brite.banking.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import static org.brite.banking.domain.EmployeePrivilege.*;

/**
 * Job role of a bank employee. The privileges are derived from the role rather than stored per
 * employee, so promoting someone is a role change and privileges can't drift between rows.
 * Each role includes everything the role below it can do.
 */
public enum EmployeeRole {
    /** Front-line staff: look up accounts and handle deposits and withdrawals. */
    TELLER(EnumSet.of(VIEW_ACCOUNT, DEPOSIT, WITHDRAW)),
    /** Runs one branch: everything a teller can do plus the account lifecycle (suspend, reactivate, close). */
    MANAGER(EnumSet.of(VIEW_ACCOUNT, DEPOSIT, WITHDRAW, SUSPEND_ACCOUNT, UPDATE_SUSPENSION,
            REACTIVATE_ACCOUNT, CLOSE_ACCOUNT, VIEW_BRANCH_REPORTS)),
    /** Oversees several branches: everything a manager can do plus managing employees. */
    AREA_MANAGER(EnumSet.allOf(EmployeePrivilege.class));

    private final Set<EmployeePrivilege> privileges;

    EmployeeRole(EnumSet<EmployeePrivilege> privileges) {
        this.privileges = Collections.unmodifiableSet(privileges);
    }

    public Set<EmployeePrivilege> getPrivileges() {
        return privileges;
    }

    public boolean hasPrivilege(EmployeePrivilege privilege) {
        return privileges.contains(privilege);
    }
}
