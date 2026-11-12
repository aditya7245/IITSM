package com.iitsm.repository;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.util.Days;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/** Small building blocks for browsing the audit history, combined with {@code and}. */
public final class AuditLogSpecifications {

    private AuditLogSpecifications() { }

    public static Specification<AuditLog> forIncident(Long incidentId) {
        return (root, query, cb) -> cb.equal(root.get("incident").get("id"), incidentId);
    }

    /** Lines written for actions of this user (lines done by the system have no user). */
    public static Specification<AuditLog> byUser(Long userId) {
        return (root, query, cb) -> cb.equal(root.get("user").get("id"), userId);
    }

    /** On or after the start of this day. */
    public static Specification<AuditLog> from(LocalDate day) {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), Days.startOf(day));
    }

    /** On or before this day, so up to the start of the next one. */
    public static Specification<AuditLog> to(LocalDate day) {
        return (root, query, cb) -> cb.lessThan(root.get("createdAt"), Days.startOfNextDay(day));
    }
}
