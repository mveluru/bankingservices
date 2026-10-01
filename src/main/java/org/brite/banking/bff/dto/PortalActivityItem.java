package org.brite.banking.bff.dto;

import org.brite.banking.domain.LocationType;
import org.brite.banking.domain.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One transaction line in an account overview's recent activity. {@code bankLocationName/Type/City/State} say which branch
 * or ATM handled it (null for transactions made without one); the employee who handled it is deliberately not exposed to customers.
 */
public record PortalActivityItem(TransactionType transactionType, BigDecimal amount, BigDecimal balanceAfter,
                                 LocalDate transactionDate, String depositType,
                                 String bankLocationName, LocationType bankLocationType, String bankLocationCity, String bankLocationState) {
}
