package org.brite.banking.bff.dto;

/**
 * What the staff portal needs right after an employee logs in, in one call: the access token, who they are (with the privileges their
 * role grants, so the UI can hide actions they can't do) and their branch card (null for an area manager). The token is a
 * credential, so {@code toString} leaves it out.
 */
public record StaffPortalLoginResponse(String accessToken, String tokenType, long expiresIn, PortalEmployee employee, PortalLocation branch) {
    @Override
    public String toString() {
        return "StaffPortalLoginResponse[tokenType=" + tokenType + ", expiresIn=" + expiresIn + ", employee=" + employee + "]";
    }
}
