package com.iitsm.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the SLA check on a timer (setting {@code iitsm.sla.monitor-interval-ms}, in milliseconds between the end
 * of one run and the start of the next). The timer lives in its own class so that the check is called through
 * Spring and not directly from inside the checking class.
 */
@Component
public class SlaMonitorScheduler {

    private final SlaMonitorService monitor;

    public SlaMonitorScheduler(SlaMonitorService monitor) {
        this.monitor = monitor;
    }

    @Scheduled(fixedDelayString = "${iitsm.sla.monitor-interval-ms}")
    public void run() {
        monitor.runOnce();
    }
}
