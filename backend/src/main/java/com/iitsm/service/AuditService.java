package com.iitsm.service;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditService {

    private final AuditLogRepository auditLogs;

    public AuditService(AuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    /** The full history of one incident, oldest first. */
    @Transactional(readOnly = true)
    public List<AuditLog> history(Long incidentId) {
        return auditLogs.findByIncidentIdOrderByCreatedAtAscIdAsc(incidentId);
    }
}
