package com.iitsm.repository;

import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.sla.SlaPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SlaPolicyRepository extends JpaRepository<SlaPolicy, Long> {

    /** The override for one service and priority, if any. */
    Optional<SlaPolicy> findByPriorityAndServiceId(Priority priority, Long serviceId);

    /** The default policy for a priority (the row with no service). */
    Optional<SlaPolicy> findByPriorityAndServiceIsNull(Priority priority);
}
