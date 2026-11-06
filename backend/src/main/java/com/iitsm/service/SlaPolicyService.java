package com.iitsm.service;

import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.exception.MissingSlaConfigurationException;
import com.iitsm.repository.SlaPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SlaPolicyService {

    private final SlaPolicyRepository policies;

    public SlaPolicyService(SlaPolicyRepository policies) {
        this.policies = policies;
    }

    /**
     * Finds the SLA policy for a priority and service: the service's own policy if it has one,
     * otherwise the default for that priority.
     *
     * @throws MissingSlaConfigurationException if neither exists
     */
    @Transactional(readOnly = true)
    public SlaPolicy findPolicy(Priority priority, ITService service) {
        if (service != null) {
            var override = policies.findByPriorityAndServiceId(priority, service.getId());
            if (override.isPresent()) {
                return override.get();
            }
        }
        return policies.findByPriorityAndServiceIsNull(priority)
                .orElseThrow(() -> new MissingSlaConfigurationException(priority.name()));
    }
}
