package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatement {
    private String accountNumber;
    private LocalDate beginDate;
    private LocalDate endDate; // Can't be in future , EndDate can be in past but at min month before than Today or Today.
    private List<AccountTransaction> transactions;
}
