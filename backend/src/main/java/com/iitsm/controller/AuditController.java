package com.iitsm.controller;

import com.iitsm.dto.AuditResponse;
import com.iitsm.dto.PageResponse;
import com.iitsm.service.AuditFacade;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** The audit history across all incidents. Administrators only. */
@RestController
@RequestMapping("/api/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditFacade audit;

    public AuditController(AuditFacade audit) {
        this.audit = audit;
    }

    /**
     * Newest first. All filters are optional: {@code incidentId}, {@code userId} (lines for actions of that
     * user) and {@code from} / {@code to} as ISO dates, both included. Paging: {@code page} from 0,
     * {@code size} (default 20, at most 100).
     */
    @GetMapping
    public PageResponse<AuditResponse> search(
            @RequestParam(required = false) Long incidentId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return audit.search(incidentId, userId, from, to, pageable);
    }
}
