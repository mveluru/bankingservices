package org.brite.banking.bff.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Settings for the banking UI portal's BFF layer ({@code banking.portal.*} in application.yml). */
@Data
@ConfigurationProperties(prefix = "banking.portal")
public class PortalProperties {
    /** Browser origins allowed to call {@code /bff/**} (the portal's dev/prod hosts). */
    private List<String> allowedOrigins = List.of("http://localhost:5173", "http://localhost:3000");
    /** Max accounts returned on the home screen. */
    private int homeAccountLimit = 5;
    /** Max branches/ATMs returned on the home screen and after opening an account. */
    private int nearbyLocationLimit = 5;
    /** Recent-activity window when the caller doesn't pass {@code days}. */
    private int defaultActivityDays = 30;
    /** Largest {@code days} a caller may ask for. */
    private int maxActivityDays = 90;
    /** Max transactions in an account overview's recent activity. */
    private int activityLimit = 20;
}
