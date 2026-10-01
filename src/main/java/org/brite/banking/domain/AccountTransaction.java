package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountTransaction {
    private String accountNumber;
    private TransactionType transactionType;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private LocalDate transactionDate;
    /** "cash" or "check"; only set for DEPOSIT transactions, null for WITHDRAWAL. */
    private String depositType;

    /** Employee who handled the transaction; null for customer-initiated ones. */
    private String employeeNumber;
    private String employeeName;
    private EmployeeRole employeeRole;
    /** Branch office or ATM where it was handled (snapshot); null when not recorded. */
    private Long bankLocationId;
    private String bankLocationName;
    private LocationType bankLocationType;
    private String bankLocationCity;
    private String bankLocationState;

    /**
     * A copy for customers: which branch or ATM handled the transaction is kept, but not which employee
     * ({@code employeeNumber}, {@code employeeName} and {@code employeeRole} are cleared). Staff identity is internal.
     */
    public AccountTransaction withoutStaffIdentity() {
        return AccountTransaction.builder().accountNumber(accountNumber).transactionType(transactionType)
                .amount(amount).balanceAfter(balanceAfter).transactionDate(transactionDate).depositType(depositType)
                .bankLocationId(bankLocationId).bankLocationName(bankLocationName).bankLocationType(bankLocationType)
                .bankLocationCity(bankLocationCity).bankLocationState(bankLocationState)
                .build();
    }
}
