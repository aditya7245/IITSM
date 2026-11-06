package com.iitsm.service;

import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.exception.MissingSlaConfigurationException;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.repository.SlaPolicyRepository;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Rolls back after each test, so the policies created or deleted here never reach other tests. */
@Transactional
class SlaPolicyServiceTest extends AbstractIntegrationTest {

    @Autowired SlaPolicyService service;
    @Autowired SlaPolicyRepository policies;
    @Autowired ITServiceRepository services;

    private ITService paymentService() {
        return services.findByName("Payment Application").orElseThrow();
    }

    @Test
    void defaultPoliciesApplyToAServiceWithoutOverrides() {
        ITService s = paymentService();
        assertTimes(service.findPolicy(Priority.P1, s), 5, 30);
        assertTimes(service.findPolicy(Priority.P2, s), 15, 120);
        assertTimes(service.findPolicy(Priority.P3, s), 60, 480);
        assertTimes(service.findPolicy(Priority.P4, s), 240, 1440);
    }

    @Test
    void serviceOverrideWinsOverTheDefault() {
        ITService s = paymentService();
        policies.save(new SlaPolicy(Priority.P1, s, 2, 10));

        assertTimes(service.findPolicy(Priority.P1, s), 2, 10);
        // Other priorities for that service still use the defaults.
        assertTimes(service.findPolicy(Priority.P2, s), 15, 120);
        // Another service is not affected.
        ITService other = services.findByName("VPN Gateway").orElseThrow();
        assertTimes(service.findPolicy(Priority.P1, other), 5, 30);
    }

    @Test
    void missingPolicyThrows() {
        policies.findByPriorityAndServiceIsNull(Priority.P4).ifPresent(policies::delete);

        MissingSlaConfigurationException e = assertThrows(MissingSlaConfigurationException.class,
                () -> service.findPolicy(Priority.P4, paymentService()));
        assertTrue(e.getMessage().contains("P4"));
    }

    private static void assertTimes(SlaPolicy policy, int response, int resolution) {
        assertEquals(response, policy.getResponseMinutes());
        assertEquals(resolution, policy.getResolutionMinutes());
    }
}
