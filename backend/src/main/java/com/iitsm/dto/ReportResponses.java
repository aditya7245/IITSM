package com.iitsm.dto;

import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;

/** The answers of the report endpoints, kept together because each is only a few fields. */
public final class ReportResponses {

    private ReportResponses() { }

    /**
     * The headline numbers. {@code open} is every incident that is not closed, cancelled or a duplicate;
     * {@code critical} is the open P1 incidents; {@code resolved} counts RESOLVED, VERIFIED and CLOSED;
     * {@code slaViolations} counts incidents whose SLA status is VIOLATED (also after they were resolved).
     * {@code averageRating} is the mean feedback rating rounded to two decimals, or null when nobody rated yet.
     */
    public record Overview(long total, long open, long critical, long resolved, long slaViolations, Double averageRating) { }

    /** A technician's active incidents out of their limit. */
    public record TechnicianWorkload(Long technicianId, String name, int active, int max) { }

    /** Incidents of one service or one category. */
    public record NamedCountResponse(Long id, String name, long count) { }

    public record PriorityCount(Priority priority, long count) { }

    public record SlaCount(SlaStatus slaStatus, long count) { }

    /** Incidents created on one day; {@code date} is an ISO date such as 2026-11-10 (days are UTC). */
    public record DayCount(String date, long count) { }
}
