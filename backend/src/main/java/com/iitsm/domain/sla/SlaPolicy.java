package com.iitsm.domain.sla;

import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.incident.Priority;
import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.*;

/**
 * How many minutes the team has to respond to and resolve an incident of a given priority.
 * A policy with no service is the default for that priority; a policy with a service overrides it for that service.
 */
@Entity
@Table(name = "sla_policies")
public class SlaPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    private Priority priority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private ITService service;

    @Column(name = "response_minutes", nullable = false)
    private int responseMinutes;

    @Column(name = "resolution_minutes", nullable = false)
    private int resolutionMinutes;

    protected SlaPolicy() { }

    public SlaPolicy(Priority priority, ITService service, int responseMinutes, int resolutionMinutes) {
        if (priority == null) {
            throw new InvalidInputException("SLA policy needs a priority");
        }
        if (responseMinutes <= 0 || resolutionMinutes <= 0) {
            throw new InvalidInputException("SLA minutes must be greater than 0");
        }
        this.priority = priority;
        this.service = service;
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }

    public Long getId() { return id; }
    public Priority getPriority() { return priority; }
    public ITService getService() { return service; }
    public int getResponseMinutes() { return responseMinutes; }
    public int getResolutionMinutes() { return resolutionMinutes; }

    /** Changes the times. The priority and the service are what identify a policy, so they stay. */
    public void changeMinutes(int responseMinutes, int resolutionMinutes) {
        if (responseMinutes <= 0 || resolutionMinutes <= 0) {
            throw new InvalidInputException("SLA minutes must be greater than 0");
        }
        this.responseMinutes = responseMinutes;
        this.resolutionMinutes = resolutionMinutes;
    }
}
