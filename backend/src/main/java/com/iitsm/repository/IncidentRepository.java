package com.iitsm.repository;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    // ------------------------------------------------------------------ numbers for the reports

    /** How many incidents have none of the given statuses (all open incidents when passed the terminal statuses). */
    long countByStatusNotIn(Collection<IncidentStatus> statuses);

    /** How many incidents have one of the given statuses. */
    long countByStatusIn(Collection<IncidentStatus> statuses);

    long countByPriority(Priority priority);

    /** How many incidents of a priority have none of the given statuses (for example the open P1 incidents). */
    long countByPriorityAndStatusNotIn(Priority priority, Collection<IncidentStatus> statuses);

    long countBySlaStatus(SlaStatus slaStatus);

    /** Incidents per service, for every service that has at least one incident. The order is not defined. */
    @Query("select new com.iitsm.repository.NamedCount(s.id, s.name, count(i)) "
            + "from Incident i join i.service s group by s.id, s.name")
    List<NamedCount> countPerService();

    /** Incidents per category, for every category that has at least one incident. The order is not defined. */
    @Query("select new com.iitsm.repository.NamedCount(c.id, c.name, count(i)) "
            + "from Incident i join i.category c group by c.id, c.name")
    List<NamedCount> countPerCategory();

    /** The creation time of every incident created from {@code windowStart} (included) until {@code windowEnd} (not included). */
    @Query("select i.createdAt from Incident i where i.createdAt >= :windowStart and i.createdAt < :windowEnd")
    List<Instant> findCreationTimesBetween(@Param("windowStart") Instant windowStart, @Param("windowEnd") Instant windowEnd);
}
