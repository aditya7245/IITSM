package com.iitsm.controller;

import com.iitsm.dto.AdminRequests;
import com.iitsm.dto.TechnicianWorkloadResponse;
import com.iitsm.dto.UserResponse;
import com.iitsm.service.IncidentSearchFacade;
import com.iitsm.service.UserAdminFacade;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Information for technicians about themselves. Team leads are technicians too. */
@RestController
@RequestMapping("/api/technicians")
public class TechnicianController {

    private final IncidentSearchFacade facade;
    private final UserAdminFacade accounts;

    public TechnicianController(IncidentSearchFacade facade, UserAdminFacade accounts) {
        this.facade = facade;
        this.accounts = accounts;
    }

    /** How many incidents count toward the logged-in technician's limit, and what the limit is. */
    @GetMapping("/me/workload")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public TechnicianWorkloadResponse myWorkload() {
        return facade.myWorkload();
    }

    /** A technician marks themselves available or not for new automatic assignments. */
    @PutMapping("/me/availability")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public UserResponse setMyAvailability(@Valid @RequestBody AdminRequests.SetAvailability request) {
        return accounts.setMyAvailability(request.available());
    }
}
