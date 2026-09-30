package org.bee.banking.bff.dto;

import java.util.List;

/** Home screen bundle: the customer's active accounts plus nearby branches/ATMs. */
public record PortalHomeResponse(long totalActiveAccounts, List<PortalAccountSummary> accounts,
                                 List<PortalLocation> nearbyLocations) {
}
