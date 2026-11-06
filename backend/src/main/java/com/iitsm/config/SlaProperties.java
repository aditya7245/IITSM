package com.iitsm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code iitsm.sla}.
 *
 * @param timeScale         speeds up SLA windows for demos (10 means a 30-minute window lasts 3 minutes)
 * @param warningThreshold  fraction of the window after which an incident is AT_RISK (0.8 = 80%)
 * @param monitorIntervalMs how often the SLA monitor runs
 */
@ConfigurationProperties(prefix = "iitsm.sla")
public record SlaProperties(double timeScale, double warningThreshold, long monitorIntervalMs) {

    public SlaProperties {
        if (timeScale <= 0) {
            throw new IllegalArgumentException("iitsm.sla.time-scale must be greater than 0");
        }
        if (warningThreshold <= 0 || warningThreshold > 1) {
            throw new IllegalArgumentException("iitsm.sla.warning-threshold must be in (0, 1]");
        }
        if (monitorIntervalMs <= 0) {
            throw new IllegalArgumentException("iitsm.sla.monitor-interval-ms must be greater than 0");
        }
    }
}
