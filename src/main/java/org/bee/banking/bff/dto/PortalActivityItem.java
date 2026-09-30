package org.bee.banking.bff.dto;

import org.bee.banking.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One transaction line in an account overview's recent activity. */
public record PortalActivityItem(TransactionType transactionType, BigDecimal amount, BigDecimal balanceAfter,
                                 LocalDate transactionDate, String depositType) {
}
