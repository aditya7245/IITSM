package com.iitsm.service;

import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.strategy.JaccardSimilarityStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests (no Spring, no database). The repository is a Proxy that answers only the one query the
 * service may use, and throws for anything else, so every test also proves the service never writes.
 * The settings come from a hand-written fake (Mockito cannot mock concrete classes on JDK 27).
 *
 * The base incident's text has 6 keywords: alpha bravo charlie delta echo foxtrot.
 */
class DuplicateDetectionServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");
    private static final String BASE_TITLE = "alpha bravo charlie";
    private static final String BASE_DESCRIPTION = "delta echo foxtrot";

    /** A settings object backed by a map; a missing key gives the default, like the real ConfigService. */
    static class FakeConfig extends ConfigService {
        final Map<String, String> values = new HashMap<>();

        FakeConfig() {
            super(null);
        }

        @Override
        public double getDouble(String key, double defaultValue) {
            return values.containsKey(key) ? Double.parseDouble(values.get(key)) : defaultValue;
        }

        @Override
        public int getInt(String key, int defaultValue) {
            return values.containsKey(key) ? Integer.parseInt(values.get(key)) : defaultValue;
        }
    }

    /** Remembers how it was asked and answers with whatever the test put in {@code results}. */
    static class FakeRepository {
        final List<Incident> results = new ArrayList<>();
        int calls;
        Long askedServiceId;
        Collection<IncidentStatus> askedStatuses;
        Instant askedAfter;

        IncidentRepository asRepository() {
            return (IncidentRepository) Proxy.newProxyInstance(IncidentRepository.class.getClassLoader(),
                    new Class<?>[]{IncidentRepository.class}, (proxy, method, args) -> {
                        if (!method.getName().equals("findByServiceIdAndStatusNotInAndCreatedAtAfter")) {
                            throw new UnsupportedOperationException("Not expected: " + method.getName());
                        }
                        calls++;
                        askedServiceId = (Long) args[0];
                        @SuppressWarnings("unchecked") Collection<IncidentStatus> statuses = (Collection<IncidentStatus>) args[1];
                        askedStatuses = statuses;
                        askedAfter = (Instant) args[2];
                        return List.copyOf(results);
                    });
        }
    }

    FakeRepository repository;
    FakeConfig config;
    DuplicateDetectionService service;
    ITService payment;
    Incident base;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
        config = new FakeConfig();
        service = new DuplicateDetectionService(repository.asRepository(), new JaccardSimilarityStrategy(), config);
        payment = new ITService("Payment Application", "Payments", (short) 5);
        setId(payment, 7L);
        base = incident(1L, "INC1", BASE_TITLE, BASE_DESCRIPTION);
    }

    private static void setId(Object entity, Long id) {
        try {
            Class<?> type = entity instanceof BaseEntity ? BaseEntity.class : entity.getClass();
            Field field = type.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Incident incident(Long id, String number, String title, String description) {
        Incident incident = new Incident(number, title, description, null, payment, null,
                (short) 2, (short) 2, 1, T0.minus(Duration.ofHours(1)));
        if (id != null) {
            setId(incident, id);
        }
        return incident;
    }

    /** A report with the first {@code keywords} of the base keywords and nothing else: score = keywords / 6. */
    private Incident sharing(Long id, int keywords) {
        String[] all = (BASE_TITLE + " " + BASE_DESCRIPTION).split(" ");
        String text = String.join(" ", java.util.Arrays.copyOfRange(all, 0, keywords));
        return incident(id, "INC" + id, text, text);
    }

    private List<Long> ids(List<DuplicateCandidate> candidates) {
        return candidates.stream().map(DuplicateCandidate::id).toList();
    }

    // ---------- the threshold ----------

    @Test
    void aScoreExactlyAtTheThresholdIsKeptAndJustBelowItIsDropped() {
        // shares alpha, bravo, charlie; 4 extra words: 3 shared of 10 distinct = exactly 0.3
        Incident exactly = incident(2L, "INC2", BASE_TITLE, "golf hotel india juliet");
        // shares alpha, bravo; 1 extra word: 2 shared of 7 distinct = 0.2857
        Incident justBelow = incident(3L, "INC3", "alpha bravo", "golf");
        repository.results.addAll(List.of(exactly, justBelow));

        List<DuplicateCandidate> found = service.findCandidates(base);

        assertEquals(List.of(2L), ids(found));
        assertEquals(0.3, found.get(0).score(), 1e-12);
    }

    @Test
    void theThresholdSettingChangesWhatIsKept() {
        repository.results.addAll(List.of(sharing(2L, 6), sharing(3L, 3)));   // scores 1.0 and 0.5

        assertEquals(List.of(2L, 3L), ids(service.findCandidates(base)));

        config.values.put(ConfigKeys.DUPLICATE_THRESHOLD, "0.6");
        assertEquals(List.of(2L), ids(service.findCandidates(base)));

        config.values.put(ConfigKeys.DUPLICATE_THRESHOLD, "0.1");
        repository.results.add(sharing(4L, 1));                               // score 0.1667 now passes
        assertEquals(List.of(2L, 3L, 4L), ids(service.findCandidates(base)));
    }

    @Test
    void dissimilarReportsAreNotReturned() {
        repository.results.add(incident(2L, "INC2", "printer jammed", "paper stuck in tray"));

        assertTrue(service.findCandidates(base).isEmpty());
    }

    @Test
    void nothingFromTheRepositoryGivesAnEmptyList() {
        assertTrue(service.findCandidates(base).isEmpty());
    }

    // ---------- order and limit ----------

    @Test
    void returnsTheFiveBestMostAlikeFirstAndTheLowerIdFirstWhenEquallyAlike() {
        // scores: id 10 -> 1.0, id 5 -> 1.0, id 20 -> 0.833, id 30 -> 0.667, id 40 -> 0.5,
        //         id 50 -> 0.333 (passes the threshold but is the sixth best), id 60 -> 0.167 (below the threshold)
        repository.results.addAll(List.of(sharing(60L, 1), sharing(50L, 2), sharing(40L, 3),
                sharing(30L, 4), sharing(20L, 5), sharing(10L, 6), sharing(5L, 6)));

        List<DuplicateCandidate> found = service.findCandidates(base);

        assertEquals(List.of(5L, 10L, 20L, 30L, 40L), ids(found));
        assertEquals(1.0, found.get(0).score(), 1e-12);
        assertEquals(5.0 / 6, found.get(2).score(), 1e-12);
    }

    // ---------- what is returned ----------

    @Test
    void eachCandidateCarriesTheIdNumberTitleAndScoreOfTheExistingIncident() {
        // shares 5 of 6 keywords and adds none: 5/6
        Incident other = incident(9L, "INC1009", "alpha bravo charlie", "delta echo");
        repository.results.add(other);

        DuplicateCandidate candidate = service.findCandidates(base).get(0);

        assertEquals(Long.valueOf(9L), candidate.id());
        assertEquals("INC1009", candidate.number());
        assertEquals("alpha bravo charlie", candidate.title());
        assertEquals(5.0 / 6, candidate.score(), 1e-12);
    }

    @Test
    void titleAndDescriptionAreComparedTogether() {
        // Same six words as the base, but split differently between title and description:
        // the combined texts are identical, so the score is 1.0.
        Incident other = incident(2L, "INC2", "alpha bravo charlie delta", "echo foxtrot");
        repository.results.add(other);

        assertEquals(1.0, service.findCandidates(base).get(0).score(), 1e-12);
    }

    // ---------- which incidents are looked at ----------

    @Test
    void theIncidentItselfIsLeftOut() {
        Incident same = incident(1L, "INC1", BASE_TITLE, BASE_DESCRIPTION);   // same id as base
        repository.results.addAll(List.of(same, sharing(2L, 6)));

        assertEquals(List.of(2L), ids(service.findCandidates(base)));
    }

    @Test
    void anUnsavedIncidentExcludesNothing() {
        Incident unsaved = incident(null, "INC99", BASE_TITLE, BASE_DESCRIPTION);
        repository.results.add(sharing(2L, 6));

        assertEquals(List.of(2L), ids(service.findCandidates(unsaved)));
    }

    @Test
    void asksOnlyForOpenIncidentsOfTheSameServiceInsideTheDefaultWindow() {
        service.findCandidates(base);

        assertEquals(1, repository.calls);
        assertEquals(Long.valueOf(7L), repository.askedServiceId);
        assertEquals(IncidentStatus.terminalStatuses(), Set.copyOf(repository.askedStatuses));
        // default window: 24 hours before the incident was created
        assertEquals(base.getCreatedAt().minus(Duration.ofHours(24)), repository.askedAfter);
    }

    @Test
    void theWindowSettingMovesTheStartOfTheWindow() {
        config.values.put(ConfigKeys.DUPLICATE_WINDOW_HOURS, "6");

        service.findCandidates(base);

        assertEquals(base.getCreatedAt().minus(Duration.ofHours(6)), repository.askedAfter);
    }

    // ---------- read only ----------

    @Test
    void doesNotChangeAnyIncident() {
        Incident other = sharing(2L, 6);
        repository.results.add(other);

        service.findCandidates(base);

        assertEquals(IncidentStatus.NEW, base.getStatus());
        assertEquals(IncidentStatus.NEW, other.getStatus());
        assertNull(base.getDuplicateOf());
        assertNull(other.getDuplicateOf());
        // the repository proxy throws for any save or delete, so reaching this line also shows nothing was written
        assertEquals(1, repository.calls);
    }

    @Test
    void aMissingIncidentIsRejected() {
        assertThrows(NullPointerException.class, () -> service.findCandidates(null));
    }
}
