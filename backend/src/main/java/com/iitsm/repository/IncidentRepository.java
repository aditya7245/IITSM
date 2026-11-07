package com.iitsm.repository;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
    Optional<Incident> findByIncidentNumber(String incidentNumber);

    /** How many incidents with one of the given statuses are assigned to a technician (their workload). */
    long countByAssigneeIdAndStatusIn(Long assigneeId, Collection<IncidentStatus> statuses);

    /** Incidents whose status is none of the given ones, for example all open incidents when passed the terminal statuses. */
    List<Incident> findByStatusNotIn(Collection<IncidentStatus> statuses);

    /** Incidents on one service, not in the given statuses, created after a point in time (used for duplicate detection). */
    List<Incident> findByServiceIdAndStatusNotInAndCreatedAtAfter(Long serviceId, Collection<IncidentStatus> statuses,
                                                                  Instant createdAfter);
}
