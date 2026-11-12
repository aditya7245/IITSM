package com.iitsm.service;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.dto.AuditResponse;
import com.iitsm.dto.PageResponse;
import com.iitsm.dto.ResponseMapper;
import com.iitsm.repository.AuditLogRepository;
import com.iitsm.repository.AuditLogSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Browsing the audit history across all incidents, for the controller. Who may do this (administrators only) is
 * declared on the controller.
 */
@Service
@Transactional(readOnly = true)
public class AuditFacade {

    private final AuditLogRepository auditLogs;
    private final ResponseMapper mapper;

    public AuditFacade(AuditLogRepository auditLogs, ResponseMapper mapper) {
        this.auditLogs = auditLogs;
        this.mapper = mapper;
    }

    /**
     * One page of history lines, newest first. Every filter that is null is not used; both dates are included.
     *
     * @param userId only lines for actions of this user
     */
    public PageResponse<AuditResponse> search(Long incidentId, Long userId, LocalDate from, LocalDate to, Pageable requested) {
        Specification<AuditLog> spec = (root, query, cb) -> cb.conjunction();
        if (incidentId != null) {
            spec = spec.and(AuditLogSpecifications.forIncident(incidentId));
        }
        if (userId != null) {
            spec = spec.and(AuditLogSpecifications.byUser(userId));
        }
        if (from != null) {
            spec = spec.and(AuditLogSpecifications.from(from));
        }
        if (to != null) {
            spec = spec.and(AuditLogSpecifications.to(to));
        }
        return PageResponse.from(auditLogs.findAll(spec, PageRequests.newestFirst(requested)), mapper::toAudit);
    }
}
