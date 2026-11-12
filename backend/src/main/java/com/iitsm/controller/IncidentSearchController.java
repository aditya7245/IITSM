package com.iitsm.controller;

import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;
import com.iitsm.dto.IncidentSummaryResponse;
import com.iitsm.dto.PageResponse;
import com.iitsm.service.IncidentSearchCriteria;
import com.iitsm.service.IncidentSearchFacade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** The incident list with its filters. Each person only gets the incidents they are allowed to see. */
@RestController
@RequestMapping("/api/incidents")
public class IncidentSearchController {

    private final IncidentSearchFacade search;

    public IncidentSearchController(IncidentSearchFacade search) {
        this.search = search;
    }

    /**
     * All filters are optional and can be combined. Dates are ISO dates such as 2026-11-12 and both ends are
     * included. Paging: {@code page} from 0 (default 0), {@code size} (default 20, at most 100) and
     * {@code sort} such as {@code createdAt,desc} (the default).
     */
    @GetMapping
    public PageResponse<IncidentSummaryResponse> search(
            @RequestParam(required = false) String incidentNumber,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) Priority priority,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long technicianId,
            @RequestParam(required = false) SlaStatus slaStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return search.search(new IncidentSearchCriteria(incidentNumber, status, priority, categoryId, serviceId,
                technicianId, slaStatus, createdFrom, createdTo), pageable);
    }
}
