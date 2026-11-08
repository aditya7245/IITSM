package com.iitsm.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Best skill first. If the category requires a skill, technicians without it are not suitable at all.
 * Ties: fewer active incidents, then more experience, then lower id.
 */
@Component
public class SkillBasedStrategy implements AssignmentStrategy {

    public static final String NAME = "SKILL_BASED";

    private static final Comparator<TechnicianCandidate> ORDER = Comparator
            .comparingInt(TechnicianCandidate::proficiency).reversed()
            .thenComparingInt(TechnicianCandidate::activeIncidents)
            .thenComparing(Comparator.comparingInt((TechnicianCandidate c) -> c.technician().getExperienceYears()).reversed())
            .thenComparing(c -> c.technician().getId(), Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<TechnicianCandidate> rank(List<TechnicianCandidate> eligible, boolean skillRequired) {
        List<TechnicianCandidate> ranked = new ArrayList<>();
        for (TechnicianCandidate candidate : eligible) {
            if (!skillRequired || candidate.proficiency() > 0) {
                ranked.add(candidate);
            }
        }
        ranked.sort(ORDER);
        return ranked;
    }
}
