package com.iitsm.service;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.dto.IncidentSummaryResponse;
import com.iitsm.dto.PageResponse;
import com.iitsm.dto.ResponseMapper;
import com.iitsm.dto.TechnicianWorkloadResponse;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.IncidentSpecifications;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.security.CurrentUserService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Searching the incident list and reading a technician's workload, for the controllers. Reads only, so no
 * incident number is taken and nothing is announced. As in {@link IncidentFacade}, the result is turned into response
 * objects before the transaction ends.
 */
@Service
@Transactional(readOnly = true)
public class IncidentSearchFacade {

    private final CurrentUserService currentUser;
    private final IncidentRepository incidents;
    private final TechnicianRepository technicians;
    private final ResponseMapper mapper;

    public IncidentSearchFacade(CurrentUserService currentUser, IncidentRepository incidents,
                                TechnicianRepository technicians, ResponseMapper mapper) {
        this.currentUser = currentUser;
        this.incidents = incidents;
        this.technicians = technicians;
        this.mapper = mapper;
    }

    /**
     * One page of the incidents that match the criteria <em>and</em> that the caller may see (see
     * {@link IncidentSpecifications#visibleTo}); the filters can only narrow what the caller already sees.
     */
    public PageResponse<IncidentSummaryResponse> search(IncidentSearchCriteria criteria, Pageable requested) {
        User user = currentUser.requireUser();
        Pageable pageable = PageRequests.forIncidentSearch(requested);

        Specification<Incident> spec = IncidentSpecifications.visibleTo(user);
        if (criteria.incidentNumber() != null && !criteria.incidentNumber().isBlank()) {
            spec = spec.and(IncidentSpecifications.numberContains(criteria.incidentNumber().strip()));
        }
        if (criteria.status() != null) {
            spec = spec.and(IncidentSpecifications.hasStatus(criteria.status()));
        }
        if (criteria.priority() != null) {
            spec = spec.and(IncidentSpecifications.hasPriority(criteria.priority()));
        }
        if (criteria.categoryId() != null) {
            spec = spec.and(IncidentSpecifications.inCategory(criteria.categoryId()));
        }
        if (criteria.serviceId() != null) {
            spec = spec.and(IncidentSpecifications.onService(criteria.serviceId()));
        }
        if (criteria.technicianId() != null) {
            spec = spec.and(IncidentSpecifications.assignedTo(criteria.technicianId()));
        }
        if (criteria.slaStatus() != null) {
            spec = spec.and(IncidentSpecifications.hasSlaStatus(criteria.slaStatus()));
        }
        if (criteria.createdFrom() != null) {
            spec = spec.and(IncidentSpecifications.createdFrom(criteria.createdFrom()));
        }
        if (criteria.createdTo() != null) {
            spec = spec.and(IncidentSpecifications.createdTo(criteria.createdTo()));
        }
        return PageResponse.from(incidents.findAll(spec, pageable), mapper::toSummary);
    }

    /** The logged-in technician's active incidents and their limit. Team leads are technicians too. */
    public TechnicianWorkloadResponse myWorkload() {
        User user = currentUser.requireUser();
        Technician technician = technicians.findById(user.getId()).orElseThrow(UnauthorizedOperationException::new);
        int active = (int) incidents.countByAssigneeIdAndStatusIn(technician.getId(), IncidentStatus.workloadStatuses());
        return new TechnicianWorkloadResponse(active, technician.getMaxWorkload());
    }
}
