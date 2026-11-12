package com.iitsm.controller;

import com.iitsm.dto.TechnicianWorkloadResponse;
import com.iitsm.service.IncidentSearchFacade;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Information for technicians about themselves. Team leads are technicians too. */
@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {

    private final IncidentSearchFacade facade;

    public TechnicianController(IncidentSearchFacade facade) {
        this.facade = facade;
    }

    /** How many incidents count toward the logged-in technician's limit, and what the limit is. */
    @GetMapping("/me/workload")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public TechnicianWorkloadResponse myWorkload() {
        return facade.myWorkload();
    }
}
