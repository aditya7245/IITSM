package com.iitsm.strategy;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Least busy first. Ties: more experience, then lower id. Skills are ignored. */
@Component
public class LoadBasedStrategy implements AssignmentStrategy {

    public static final String NAME = "LOAD_BASED";

    private static final Comparator<TechnicianCandidate> ORDER = Comparator
            .comparingInt(TechnicianCandidate::activeIncidents)
            .thenComparing(Comparator.comparingInt((TechnicianCandidate c) -> c.technician().getExperienceYears()).reversed())
            .thenComparing(c -> c.technician().getId(), Comparator.nullsLast(Comparator.naturalOrder()));

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<TechnicianCandidate> rank(List<TechnicianCandidate> eligible, boolean skillRequired) {
        List<TechnicianCandidate> ranked = new ArrayList<>(eligible);
        ranked.sort(ORDER);
        return ranked;
    }
}
