package org.bee.banking.bff.dto;

import java.util.List;

/**
 * Home screen bundle: the newest active and suspended accounts (closed are omitted; each row says whether it is
 * suspended and until when), the active/suspended totals, and nearby branches/ATMs.
 */
public record PortalHomeResponse(long totalActiveAccounts, long totalSuspendedAccounts, List<PortalAccountSummary> accounts,
                                 List<PortalLocation> nearbyLocations) {
}
