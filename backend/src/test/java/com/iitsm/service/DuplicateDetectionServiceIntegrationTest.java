package com.iitsm.service;

import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.User;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Duplicate detection against the real database and the real similarity strategy.
 * The incident being checked is "Payment application unavailable / Users cannot complete payment transactions"
 * (6 keywords: payment application unavailable users complete transactions). Rolls back after each test.
 */
@Transactional
class DuplicateDetectionServiceIntegrationTest extends AbstractIntegrationTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    @Autowired DuplicateDetectionService detection;
    @Autowired ConfigService config;
    @Autowired IncidentRepository incidents;
    @Autowired UserRepository users;
    @Autowired ITServiceRepository services;
    @Autowired CategoryRepository categories;

    User reporter;
    ITService payment;
    ITService vpn;
    Category category;
    Incident checked;
    int serial;

    @BeforeEach
    void setUp() {
        reporter = users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow();
        payment = services.findByName("Payment Application").orElseThrow();
        vpn = services.findByName("VPN Gateway").orElseThrow();
        category = categories.findByName("Application").orElseThrow();
        checked = save(payment, "Payment application unavailable", "Users cannot complete payment transactions", T0);
    }

    private Incident save(ITService service, String title, String description, Instant createdAt) {
        Incident incident = new Incident("D-" + (++serial), title, description, reporter, service, category,
                (short) 2, (short) 2, 1, createdAt);
        return incidents.saveAndFlush(incident);
    }

    /** Same service and the same problem in other words: "down" and "unavailable" are synonyms, so the score is 1.0. */
    private Incident similar(Instant createdAt) {
        return save(payment, "Payment application down", "Users cannot complete payment transactions", createdAt);
    }

    private List<String> numbers(List<DuplicateCandidate> candidates) {
        return candidates.stream().map(DuplicateCandidate::number).toList();
    }

    @Test
    void onlyTheSimilarOpenIncidentOnTheSameServiceIsFound() {
        Incident wanted = similar(T0.minus(Duration.ofHours(1)));
        save(payment, "Printer jammed on floor three", "Paper stuck in the tray", T0.minus(Duration.ofHours(1)));   // not similar
        save(vpn, "Payment application down", "Users cannot complete payment transactions",
                T0.minus(Duration.ofHours(1)));                                                                    // other service

        List<DuplicateCandidate> found = detection.findCandidates(checked);

        assertEquals(1, found.size());
        DuplicateCandidate candidate = found.get(0);
        assertEquals(wanted.getId(), candidate.id());
        assertEquals(wanted.getIncidentNumber(), candidate.number());
        assertEquals("Payment application down", candidate.title());
        assertEquals(1.0, candidate.score(), 1e-9);
    }

    @Test
    void theIncidentBeingCheckedIsNeverItsOwnDuplicate() {
        // "checked" is saved and is identical to itself, yet nothing is returned
        assertTrue(detection.findCandidates(checked).isEmpty());
    }

    @Test
    void closedCancelledAndDuplicateIncidentsAreNotOpenSoTheyAreSkipped() {
        Incident original = similar(T0.minus(Duration.ofHours(1)));

        Incident cancelled = similar(T0.minus(Duration.ofHours(2)));
        cancelled.cancel();
        incidents.saveAndFlush(cancelled);

        Incident markedDuplicate = similar(T0.minus(Duration.ofHours(3)));
        markedDuplicate.markDuplicateOf(original);
        incidents.saveAndFlush(markedDuplicate);

        assertEquals(List.of(original.getIncidentNumber()), numbers(detection.findCandidates(checked)));
    }

    @Test
    void anIncidentThatIsOpenButFurtherAlongIsStillACandidate() {
        Incident classified = similar(T0.minus(Duration.ofHours(1)));
        classified.classify(Priority.P2, 12, T0.plusSeconds(900), T0.plusSeconds(7200));
        incidents.saveAndFlush(classified);

        assertEquals(IncidentStatus.CLASSIFIED, classified.getStatus());
        assertEquals(List.of(classified.getIncidentNumber()), numbers(detection.findCandidates(checked)));
    }

    @Test
    void incidentsOlderThanTheWindowAreSkippedAndTheWindowSettingIsRespected() {
        Incident inside = similar(T0.minus(Duration.ofHours(23)));
        Incident outside = similar(T0.minus(Duration.ofHours(25)));

        assertEquals(List.of(inside.getIncidentNumber()), numbers(detection.findCandidates(checked)));

        config.set(ConfigKeys.DUPLICATE_WINDOW_HOURS, "48");

        assertEquals(List.of(inside.getIncidentNumber(), outside.getIncidentNumber()),
                numbers(detection.findCandidates(checked)));   // equally alike, so the lower id comes first
    }

    @Test
    void theThresholdSettingIsRespected() {
        save(payment, "Payment application slow", "Users cannot complete payment transactions",
                T0.minus(Duration.ofHours(1)));   // shares 5 of 7 keywords: 0.714

        config.set(ConfigKeys.DUPLICATE_THRESHOLD, "0.9");

        assertTrue(detection.findCandidates(checked).isEmpty());
    }

    @Test
    void theSeededSettingsAreUsedByDefault() {
        // V2/V4 seed: threshold 0.3 and a 24 hour window; the service must be reading them from system_config
        assertEquals(0.3, config.getDouble(ConfigKeys.DUPLICATE_THRESHOLD, -1), 1e-12);
        assertEquals(24, config.getInt(ConfigKeys.DUPLICATE_WINDOW_HOURS, -1));
        similar(T0.minus(Duration.ofHours(1)));

        assertEquals(1, detection.findCandidates(checked).size());
    }

    @Test
    void checkingForDuplicatesDoesNotChangeAnyIncident() {
        Incident other = similar(T0.minus(Duration.ofHours(1)));

        detection.findCandidates(checked);
        incidents.flush();

        assertEquals(IncidentStatus.NEW, checked.getStatus());
        assertEquals(IncidentStatus.NEW, other.getStatus());
        assertNull(checked.getDuplicateOf());
        assertNull(other.getDuplicateOf());
    }
}
