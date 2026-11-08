package com.iitsm.observer;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.repository.AuditLogRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Writes one permanent history line for every incident event.
 * It runs first and on the caller's thread, so it joins the caller's database transaction: if writing the
 * history fails, the change that caused the event is rolled back too. There is never a change without a record.
 */
@Component
@Order(1)
public class AuditObserver implements IncidentObserver {

    private final AuditLogRepository auditLogs;

    public AuditObserver(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Override
    public void onEvent(IncidentEvent event) {
        auditLogs.save(new AuditLog(
                event.incident(),
                event.actor(),
                event.type().name(),
                name(event.previous()),
                name(event.current()),
                event.description(),
                event.at()));
    }

    private static String name(IncidentStatus status) {
        return status == null ? null : status.name();
    }
}
