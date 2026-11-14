package com.iitsm.controller;

import com.iitsm.dto.ReportResponses;
import com.iitsm.service.ReportFacade;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The numbers for the dashboard. Administrators (administrators and IT managers) only. */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasRole('ADMIN')")
public class ReportController {

    private final ReportFacade reports;

    public ReportController(ReportFacade reports) {
        this.reports = reports;
    }

    @GetMapping("/overview")
    public ReportResponses.Overview overview() {
        return reports.overview();
    }

    @GetMapping("/technician-workload")
    public List<ReportResponses.TechnicianWorkload> technicianWorkload() {
        return reports.technicianWorkload();
    }

    @GetMapping("/service-analysis")
    public List<ReportResponses.NamedCountResponse> serviceAnalysis() {
        return reports.serviceAnalysis();
    }

    @GetMapping("/category-distribution")
    public List<ReportResponses.NamedCountResponse> categoryDistribution() {
        return reports.categoryDistribution();
    }

    @GetMapping("/priority-distribution")
    public List<ReportResponses.PriorityCount> priorityDistribution() {
        return reports.priorityDistribution();
    }

    @GetMapping("/sla-compliance")
    public List<ReportResponses.SlaCount> slaCompliance() {
        return reports.slaCompliance();
    }

    /** Incidents created per day for the last {@code days} days (default 14, at most 90), today included. */
    @GetMapping("/incidents-per-day")
    public List<ReportResponses.DayCount> incidentsPerDay(@RequestParam(required = false) Integer days) {
        return reports.incidentsPerDay(days);
    }
}
