package org.brite.banking.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** What an administrator sees after changing a login's status; no hash, no failure counters. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginStatusView {
    private String username;
    private LoginStatus status;
    private String statusReason;
    private LocalDateTime statusChangedAt;
    private LocalDateTime lockedUntil;
}
