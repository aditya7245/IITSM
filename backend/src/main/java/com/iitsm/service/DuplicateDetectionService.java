package com.iitsm.service;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.strategy.SimilarityStrategy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Finds existing incidents that look like a given one, so the reporter (or an admin) can be warned about
 * possible duplicates. A candidate must be open, be about the same service, and have been created within the
 * look-back window (setting {@code duplicate.window.hours}, default 24) before the incident being checked.
 * Its title and description, joined into one text, must score at least {@code duplicate.threshold} (default 0.3)
 * against the checked incident's. At most the 5 best matches are returned.
 * <p>
 * This service only reads. It never changes any incident; what to do with the answer is up to the caller.
 */
@Service
public class DuplicateDetectionService {

    static final int MAX_CANDIDATES = 5;
    static final int DEFAULT_WINDOW_HOURS = 24;
    static final double DEFAULT_THRESHOLD = 0.3;

    private final IncidentRepository incidents;
    private final SimilarityStrategy similarity;
    private final ConfigService config;

    public DuplicateDetectionService(IncidentRepository incidents, SimilarityStrategy similarity, ConfigService config) {
        this.incidents = incidents;
        this.similarity = similarity;
        this.config = config;
    }

    /**
     * @return up to 5 possible duplicates, most alike first (equally alike ones: the lower id first);
     *         an empty list if nothing is similar enough
     */
    @Transactional(readOnly = true)
    public List<DuplicateCandidate> findCandidates(Incident incident) {
        Objects.requireNonNull(incident, "incident");

        double threshold = config.getDouble(ConfigKeys.DUPLICATE_THRESHOLD, DEFAULT_THRESHOLD);
        int windowHours = config.getInt(ConfigKeys.DUPLICATE_WINDOW_HOURS, DEFAULT_WINDOW_HOURS);
        // The window is measured from when this incident was reported, not from "now", so the answer
        // does not change just because time passes.
        Instant windowStart = incident.getCreatedAt().minus(Duration.ofHours(windowHours));
        String text = textOf(incident);

        return incidents.findByServiceIdAndStatusNotInAndCreatedAtAfter(
                        incident.getService().getId(), IncidentStatus.terminalStatuses(), windowStart).stream()
                .filter(other -> !isSameIncident(incident, other))
                .map(other -> new DuplicateCandidate(other.getId(), other.getIncidentNumber(), other.getTitle(),
                        similarity.similarity(text, textOf(other))))
                .filter(candidate -> candidate.score() >= threshold)
                .sorted(Comparator.comparingDouble(DuplicateCandidate::score).reversed()
                        .thenComparing(DuplicateCandidate::id))
                .limit(MAX_CANDIDATES)
                .toList();
    }

    private static String textOf(Incident incident) {
        return incident.getTitle() + " " + incident.getDescription();
    }

    /** An incident that has not been saved yet has no id, so it cannot be in the database results anyway. */
    private static boolean isSameIncident(Incident incident, Incident other) {
        return incident.getId() != null && incident.getId().equals(other.getId());
    }
}
