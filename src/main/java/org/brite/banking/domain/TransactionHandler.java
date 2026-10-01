package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Who handled a deposit/withdrawal and where: the employee and the branch office or ATM,
 * snapshotted onto the transaction so the record stays correct if the employee moves or the
 * location is renamed. Any field may be null (an area manager has no branch; customer-initiated
 * transactions have no handler at all).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionHandler {
    private String employeeNumber;
    private String employeeName;
    private EmployeeRole employeeRole;
    private Long bankLocationId;
    private String bankLocationName;
    private LocationType bankLocationType;
    private String bankLocationCity;
    private String bankLocationState;
}
