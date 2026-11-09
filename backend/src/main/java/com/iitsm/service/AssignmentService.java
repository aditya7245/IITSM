package com.iitsm.service;

import com.iitsm.domain.catalog.Skill;
import com.iitsm.domain.catalog.TechnicianSkill;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.user.Technician;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.exception.WorkloadExceededException;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.TechnicianRepository;
import com.iitsm.repository.TechnicianSkillRepository;
import com.iitsm.strategy.AssignmentStrategy;
import com.iitsm.strategy.SkillBasedStrategy;
import com.iitsm.strategy.TechnicianCandidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Finds the right technician for an incident. It works out who is eligible (active, available, under their
 * workload limit), then lets the strategy chosen by the admin rank them. It never changes the incident:
 * the caller decides what to do with the answer.
 */
@Service
public class AssignmentService {

    private static final Logger log = LoggerFactory.getLogger(AssignmentService.class);

    private final TechnicianRepository technicians;
    private final IncidentRepository incidents;
    private final TechnicianSkillRepository technicianSkills;
    private final ConfigService config;
    private final Map<String, AssignmentStrategy> strategiesByName = new HashMap<>();

    public AssignmentService(TechnicianRepository technicians, IncidentRepository incidents,
                             TechnicianSkillRepository technicianSkills, ConfigService config,
                             List<AssignmentStrategy> strategies) {
        this.technicians = technicians;
        this.incidents = incidents;
        this.technicianSkills = technicianSkills;
        this.config = config;
        for (AssignmentStrategy strategy : strategies) {
            strategiesByName.put(strategy.name(), strategy);
        }
    }

    /**
     * The best eligible technician according to the active strategy, or empty if nobody is suitable.
     * The strategy is told who was chosen (round-robin needs that to take turns).
     */
    @Transactional(readOnly = true)
    public Optional<Technician> autoAssign(Incident incident) {
        AssignmentStrategy strategy = activeStrategy();
        List<TechnicianCandidate> ranked = rank(incident, strategy);
        if (ranked.isEmpty()) {
            return Optional.empty();
        }
        Technician chosen = ranked.get(0).technician();
        strategy.recordAssigned(chosen);
        return Optional.of(chosen);
    }

    /** The best {@code limit} technicians for the admin to choose from. Nothing is recorded or changed. */
    @Transactional(readOnly = true)
    public List<Technician> recommend(Incident incident, int limit) {
        if (limit < 1) {
            throw new InvalidInputException("The number of recommendations must be at least 1");
        }
        return rank(incident, activeStrategy()).stream()
                .limit(limit)
                .map(TechnicianCandidate::technician)
                .toList();
    }

    /**
     * Checks that an admin's chosen technician can take another incident. Skills are not required for a manual choice.
     *
     * @throws TechnicianUnavailableException if the technician does not exist, is inactive, or marked themselves unavailable
     * @throws WorkloadExceededException      if the technician already has their maximum number of active incidents
     */
    @Transactional(readOnly = true)
    public Technician assignManually(Incident incident, Long technicianId) {
        Technician technician = technicianId == null ? null : technicians.findById(technicianId).orElse(null);
        if (technician == null) {
            throw new TechnicianUnavailableException("technician " + technicianId + " does not exist");
        }
        if (!technician.isActive() || !technician.isAvailable()) {
            throw new TechnicianUnavailableException(technician.getFullName() + " is not available");
        }
        technician.requireCapacity(activeIncidents(technician));
        return technician;
    }

    // ------------------------------------------------------------------ helpers

    private List<TechnicianCandidate> rank(Incident incident, AssignmentStrategy strategy) {
        Skill requiredSkill = incident.getCategory() == null ? null : incident.getCategory().getRequiredSkill();
        Long requiredSkillId = requiredSkill == null ? null : requiredSkill.getId();
        return strategy.rank(candidates(requiredSkillId), requiredSkillId != null);
    }

    /** Everyone who may take work, with their current workload and their level in the required skill. */
    private List<TechnicianCandidate> candidates(Long requiredSkillId) {
        List<Technician> available = technicians.findByActiveTrueAndAvailableTrue();

        Map<Long, Short> proficiencyByTechnician = Map.of();
        if (requiredSkillId != null && !available.isEmpty()) {
            List<Long> ids = available.stream().map(Technician::getId).toList();
            proficiencyByTechnician = technicianSkills.findByTechnicianIdIn(ids).stream()
                    .filter(skill -> requiredSkillId.equals(skill.getSkillId()))
                    .collect(Collectors.toMap(TechnicianSkill::getTechnicianId, TechnicianSkill::getProficiency));
        }

        List<TechnicianCandidate> candidates = new ArrayList<>();
        for (Technician technician : available) {
            int active = activeIncidents(technician);
            if (active >= technician.getMaxWorkload()) {
                continue;   // full
            }
            int proficiency = proficiencyByTechnician.getOrDefault(technician.getId(), (short) 0);
            candidates.add(new TechnicianCandidate(technician, active, proficiency));
        }
        return candidates;
    }

    private int activeIncidents(Technician technician) {
        return (int) incidents.countByAssigneeIdAndStatusIn(technician.getId(), IncidentStatus.workloadStatuses());
    }

    private AssignmentStrategy activeStrategy() {
        String name = config.getString(ConfigKeys.ASSIGNMENT_STRATEGY, SkillBasedStrategy.NAME)
                .trim().toUpperCase(Locale.ROOT);
        AssignmentStrategy strategy = strategiesByName.get(name);
        if (strategy == null) {
            log.warn("Unknown assignment strategy '{}'; using {}", name, SkillBasedStrategy.NAME);
            strategy = strategiesByName.get(SkillBasedStrategy.NAME);
        }
        return strategy;
    }
}
