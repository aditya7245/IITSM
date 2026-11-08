package com.iitsm.strategy;

import com.iitsm.domain.user.Technician;

import java.util.List;

/**
 * Chooses who should get an incident (Strategy pattern). Each implementation is one way of ranking
 * the technicians who are already known to be eligible (active, available and under their workload limit).
 * The admin picks the active strategy by name, so strategies can be swapped without changing any other code.
 */
public interface AssignmentStrategy {

    /** Unique name stored in the settings table, for example SKILL_BASED. */
    String name();

    /**
     * @param eligible      technicians who may take the incident
     * @param skillRequired true if the incident's category requires a skill
     * @return the suitable technicians, best first; empty if nobody is suitable. The input list is not changed.
     */
    List<TechnicianCandidate> rank(List<TechnicianCandidate> eligible, boolean skillRequired);

    /** Called after a technician was actually given an incident. Only strategies that remember history need this. */
    default void recordAssigned(Technician technician) { }
}
