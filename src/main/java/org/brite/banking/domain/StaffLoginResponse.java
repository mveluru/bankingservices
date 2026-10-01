package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Result of a successful employee login: the signed token plus the employee profile (with role privileges). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "accessToken")
public class StaffLoginResponse {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    /** Seconds until the token expires. */
    private long expiresIn;
    private Employee employee;
}
