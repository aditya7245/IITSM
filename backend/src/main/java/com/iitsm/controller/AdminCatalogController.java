package com.iitsm.controller;

import com.iitsm.dto.AdminRequests;
import com.iitsm.dto.AssignmentStrategyResponse;
import com.iitsm.dto.TeamLeadAssignmentResponse;
import com.iitsm.dto.CategoryResponse;
import com.iitsm.dto.ServiceResponse;
import com.iitsm.dto.SlaPolicyResponse;
import com.iitsm.service.CatalogFacade;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administrators manage IT services, categories, SLA times and the assignment strategy. Services and categories
 * cannot be deleted, only switched off with {@code active=false}. Everything here is for administrators only.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminCatalogController {

    private final CatalogFacade catalog;

    public AdminCatalogController(CatalogFacade catalog) {
        this.catalog = catalog;
    }

    // ------------------------------------------------------------------ IT services

    /** All services, including the switched-off ones. */
    @GetMapping("/services")
    public List<ServiceResponse> services() {
        return catalog.allServices();
    }

    @PostMapping("/services")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse createService(@Valid @RequestBody AdminRequests.ServiceRequest request) {
        return catalog.createService(request);
    }

    @PutMapping("/services/{id}")
    public ServiceResponse updateService(@PathVariable Long id, @Valid @RequestBody AdminRequests.ServiceRequest request) {
        return catalog.updateService(id, request);
    }

    // ------------------------------------------------------------------ categories

    /** All categories, including the switched-off ones. */
    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalog.allCategories();
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryResponse createCategory(@Valid @RequestBody AdminRequests.CategoryRequest request) {
        return catalog.createCategory(request);
    }

    @PutMapping("/categories/{id}")
    public CategoryResponse updateCategory(@PathVariable Long id, @Valid @RequestBody AdminRequests.CategoryRequest request) {
        return catalog.updateCategory(id, request);
    }

    // ------------------------------------------------------------------ SLA policies

    @GetMapping("/sla-policies")
    public List<SlaPolicyResponse> slaPolicies() {
        return catalog.slaPolicies();
    }

    /** A second policy for the same priority and service is a 409. */
    @PostMapping("/sla-policies")
    @ResponseStatus(HttpStatus.CREATED)
    public SlaPolicyResponse createSlaPolicy(@Valid @RequestBody AdminRequests.CreateSlaPolicy request) {
        return catalog.createSlaPolicy(request);
    }

    @PutMapping("/sla-policies/{id}")
    public SlaPolicyResponse updateSlaPolicy(@PathVariable Long id, @Valid @RequestBody AdminRequests.UpdateSlaPolicy request) {
        return catalog.updateSlaPolicy(id, request);
    }

    /** Only the override of one service can be deleted, never a default policy. */
    @DeleteMapping("/sla-policies/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteSlaPolicy(@PathVariable Long id) {
        catalog.deleteSlaPolicy(id);
    }

    // ------------------------------------------------------------------ assignment strategy

    @GetMapping("/config/assignment-strategy")
    public AssignmentStrategyResponse assignmentStrategy() {
        return catalog.assignmentStrategy();
    }

    /** The {@code strategy} must be one of the available names, otherwise 400. It applies from the next automatic assignment. */
    @PutMapping("/config/assignment-strategy")
    public AssignmentStrategyResponse setAssignmentStrategy(@Valid @RequestBody AdminRequests.AssignmentStrategy request) {
        return catalog.setAssignmentStrategy(request.strategy());
    }

    // ------------------------------------------------------------------ team leads in automatic assignment

    @GetMapping("/config/team-lead-assignment")
    public TeamLeadAssignmentResponse teamLeadAssignment() {
        return catalog.teamLeadAssignment();
    }

    /** {@code enabled} true lets team leads be assigned new incidents automatically, false keeps them free for escalations. */
    @PutMapping("/config/team-lead-assignment")
    public TeamLeadAssignmentResponse setTeamLeadAssignment(@Valid @RequestBody AdminRequests.TeamLeadAssignment request) {
        return catalog.setTeamLeadAssignment(request.enabled());
    }
}
