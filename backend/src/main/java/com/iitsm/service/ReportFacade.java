package com.iitsm.service;

import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;
import com.iitsm.domain.user.Technician;
import com.iitsm.dto.ReportResponses;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.repository.FeedbackRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.NamedCount;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.util.Days;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The numbers behind the dashboard. Everything is counted over all incidents ever reported, whatever their status,
 * unless a report says otherwise. Reads only. Who may ask (administrators only) is declared on the controller.
 */
@Service
@Transactional(readOnly = true)
public class ReportFacade {

    static final int DEFAULT_DAYS = 14;
    static final int MAX_DAYS = 90;

    private static final Set<IncidentStatus> RESOLVED_STATUSES =
            EnumSet.of(IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED);

    private final IncidentRepository incidents;
    private final FeedbackRepository feedback;
    private final TechnicianRepository technicians;
    private final Clock clock;

    public ReportFacade(IncidentRepository incidents, FeedbackRepository feedback, TechnicianRepository technicians, Clock clock) {
        this.incidents = incidents;
        this.feedback = feedback;
        this.technicians = technicians;
        this.clock = clock;
    }

    public ReportResponses.Overview overview() {
        Set<IncidentStatus> terminal = IncidentStatus.terminalStatuses();
        Double average = feedback.averageRating();
        return new ReportResponses.Overview(
                incidents.count(),
                incidents.countByStatusNotIn(terminal),
                incidents.countByPriorityAndStatusNotIn(Priority.P1, terminal),
                incidents.countByStatusIn(RESOLVED_STATUSES),
                incidents.countBySlaStatus(SlaStatus.VIOLATED),
                average == null ? null : Math.round(average * 100.0) / 100.0);
    }

    /**
     * Every technician with an active account, team leads included, by name, with the number of incidents counting
     * toward their limit. It uses the same count as the technician's own workload, so the two always agree.
     */
    public List<ReportResponses.TechnicianWorkload> technicianWorkload() {
        List<ReportResponses.TechnicianWorkload> rows = new ArrayList<>();
        for (Technician technician : technicians.findByActiveTrueOrderByFullNameAsc()) {
            int active = (int) incidents.countByAssigneeIdAndStatusIn(technician.getId(), IncidentStatus.workloadStatuses());
            rows.add(new ReportResponses.TechnicianWorkload(technician.getId(), technician.getFullName(), active,
                    technician.getMaxWorkload()));
        }
        return rows;
    }

    /** Incidents per service, the service with most incidents first. Services without incidents are not listed. */
    public List<ReportResponses.NamedCountResponse> serviceAnalysis() {
        return mostFirst(incidents.countPerService());
    }

    /** Incidents per category, the category with most incidents first. Categories without incidents are not listed. */
    public List<ReportResponses.NamedCountResponse> categoryDistribution() {
        return mostFirst(incidents.countPerCategory());
    }

    /** All four priorities, P1 first, including those with no incident. */
    public List<ReportResponses.PriorityCount> priorityDistribution() {
        List<ReportResponses.PriorityCount> rows = new ArrayList<>();
        for (Priority priority : Priority.values()) {
            rows.add(new ReportResponses.PriorityCount(priority, incidents.countByPriority(priority)));
        }
        return rows;
    }

    /** All three SLA states, ON_TRACK first, including those with no incident. */
    public List<ReportResponses.SlaCount> slaCompliance() {
        List<ReportResponses.SlaCount> rows = new ArrayList<>();
        for (SlaStatus status : SlaStatus.values()) {
            rows.add(new ReportResponses.SlaCount(status, incidents.countBySlaStatus(status)));
        }
        return rows;
    }

    /**
     * Incidents created per day over the last {@code days} days, today included, oldest day first. Days without
     * incidents appear with 0. Days are UTC. {@code days} defaults to 14 and must be 1 to 90.
     */
    public List<ReportResponses.DayCount> incidentsPerDay(Integer days) {
        int count = days == null ? DEFAULT_DAYS : days;
        if (count < 1 || count > MAX_DAYS) {
            throw new InvalidInputException("days must be between 1 and " + MAX_DAYS);
        }
        LocalDate today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate first = today.minusDays(count - 1L);

        List<Instant> created = incidents.findCreationTimesBetween(Days.startOf(first), Days.startOfNextDay(today));
        Map<LocalDate, Long> perDay = created.stream().collect(
                Collectors.groupingBy(time -> time.atZone(ZoneOffset.UTC).toLocalDate(), Collectors.counting()));

        List<ReportResponses.DayCount> rows = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            rows.add(new ReportResponses.DayCount(day.toString(), perDay.getOrDefault(day, 0L)));
        }
        return rows;
    }

    private static List<ReportResponses.NamedCountResponse> mostFirst(List<NamedCount> counts) {
        return counts.stream()
                .sorted(Comparator.comparing(NamedCount::count).reversed().thenComparing(NamedCount::name))
                .map(row -> new ReportResponses.NamedCountResponse(row.id(), row.name(), row.count()))
                .toList();
    }
}
