package org.bee.banking.bff.dto;

import java.util.List;

/** Result of opening an account: the new account plus branches/ATMs in the customer's state. */
public record OpenAccountResponse(AccountOverviewResponse account, List<PortalLocation> nearbyLocations) {
}
