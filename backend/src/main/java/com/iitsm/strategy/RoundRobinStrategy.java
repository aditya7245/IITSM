package com.iitsm.strategy;

import com.iitsm.domain.user.Technician;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Takes turns. Technicians are ordered by id and the list starts just after the technician who was assigned last,
 * wrapping around at the end. The last assigned id is remembered in memory only, so after a restart it starts again
 * from the lowest id.
 */
@Component
public class RoundRobinStrategy implements AssignmentStrategy {

    public static final String NAME = "ROUND_ROBIN";

    private final AtomicReference<Long> lastAssignedId = new AtomicReference<>();

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<TechnicianCandidate> rank(List<TechnicianCandidate> eligible, boolean skillRequired) {
        List<TechnicianCandidate> byId = new ArrayList<>(eligible);
        byId.sort(Comparator.comparing(c -> c.technician().getId(), Comparator.nullsLast(Comparator.naturalOrder())));

        Long last = lastAssignedId.get();
        if (last == null) {
            return byId;
        }
        int start = 0;
        while (start < byId.size() && byId.get(start).technician().getId() <= last) {
            start++;
        }
        List<TechnicianCandidate> rotated = new ArrayList<>(byId.subList(start, byId.size()));
        rotated.addAll(byId.subList(0, start));
        return rotated;
    }

    @Override
    public void recordAssigned(Technician technician) {
        lastAssignedId.set(technician.getId());
    }
}
