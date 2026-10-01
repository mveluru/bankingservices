package org.brite.banking.bff.dto;

import org.brite.banking.domain.AuthenticatedCustomer;

/**
 * What the portal needs right after a customer logs in, in one call: the access token, who the customer is, and the home screen
 * data (so the portal doesn't need a second round trip before it can draw anything). The token is a credential, so
 * {@code toString} leaves it out.
 */
public record PortalLoginResponse(String accessToken, String tokenType, long expiresIn, AuthenticatedCustomer customer,
                                  PortalHomeResponse home) {
    @Override
    public String toString() {
        return "PortalLoginResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", customer=" + customer + "]";
    }
}
