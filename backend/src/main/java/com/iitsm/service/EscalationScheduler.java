package com.iitsm.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the escalation check on a timer, on the same interval as the SLA monitor
 * (setting {@code iitsm.sla.monitor-interval-ms}). Kept in its own class so that the check is called through Spring.
 */
@Component
public class EscalationScheduler {

    private final EscalationService escalation;

    public EscalationScheduler(EscalationService escalation) {
        this.escalation = escalation;
    }

    @Scheduled(fixedDelayString = "${iitsm.sla.monitor-interval-ms}")
    public void run() {
        escalation.runOnce();
    }
}
