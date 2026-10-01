package org.bee.configs;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.sms")
@Getter
@Setter
public class BriteSmsNotificationConfigValues {
    private boolean enabled;
    private String senderId;
    private int dailyLimit;
}
