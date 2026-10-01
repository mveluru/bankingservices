package org.brite.banking.domain;

/** A single action a bank employee is allowed to perform on customer accounts or staff. */
public enum EmployeePrivilege {
    VIEW_ACCOUNT,
    DEPOSIT,
    WITHDRAW,
    SUSPEND_ACCOUNT,
    UPDATE_SUSPENSION,
    REACTIVATE_ACCOUNT,
    CLOSE_ACCOUNT,
    VIEW_BRANCH_REPORTS,
    MANAGE_EMPLOYEES
}
