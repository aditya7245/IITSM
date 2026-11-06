package com.iitsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code iitsm.escalation}.
 *
 * @param levelTimeoutMinutes minutes (before time-scaling) to wait before escalating to the next level
 */
@ConfigurationProperties(prefix = "iitsm.escalation")
public record EscalationProperties(long levelTimeoutMinutes) {

    public EscalationProperties {
        if (levelTimeoutMinutes <= 0) {
            throw new IllegalArgumentException("iitsm.escalation.level-timeout-minutes must be greater than 0");
        }
    }
}
