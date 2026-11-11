package com.iitsm.service;

import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.Feedback;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.Technician;
import com.iitsm.exception.IncidentNotFoundException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.FeedbackRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.security.IncidentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of {@link FeedbackService}. The repositories are Proxies answering only the calls the service
 * may make; the incident rules and the access policy are the real ones.
 */
class FeedbackServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    final Map<Long, Incident> stored = new HashMap<>();
    final List<Feedback> savedFeedback = new ArrayList<>();
    final List<String> incidentSaves = new ArrayList<>();
    final List<IncidentEvent> published = new ArrayList<>();
    boolean feedbackAlreadyExists;

    Employee reporter;
    Employee stranger;
    Technician tech;
    Administrator admin;
    FeedbackService service;
    long nextId = 100;

    @BeforeEach
    void setUp() {
        reporter = withId(new Employee("emp1@iitsm.local", "h", "Emma Employee", "Finance"), 1L);
        stranger = withId(new Employee("emp2@iitsm.local", "h", "Sam Stranger", "HR"), 2L);
        tech = withId(new Technician("tech1@iitsm.local", "h", "Tom Technician", null, 5, 4), 3L);
        admin = withId(new Administrator("admin@iitsm.local", "h", "Ada Admin"), 4L);

        IncidentRepository incidents = (IncidentRepository) Proxy.newProxyInstance(
                IncidentRepository.class.getClassLoader(), new Class<?>[]{IncidentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.ofNullable(stored.get((Long) args[0]));
                    case "save" -> {
                        Incident incident = (Incident) args[0];
                        incidentSaves.add(incident.getIncidentNumber() + " " + incident.getStatus());
                        yield incident;
                    }
                    default -> throw new UnsupportedOperationException("Not expected: " + method.getName());
                });
        FeedbackRepository feedbacks = (FeedbackRepository) Proxy.newProxyInstance(
                FeedbackRepository.class.getClassLoader(), new Class<?>[]{FeedbackRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "existsByIncidentId" -> feedbackAlreadyExists;
                    case "save" -> {
                        savedFeedback.add((Feedback) args[0]);
                        yield args[0];
                    }
                    default -> throw new UnsupportedOperationException("Not expected: " + method.getName());
                });
        service = new FeedbackService(incidents, feedbacks, new IncidentEventPublisher(List.of(published::add)),
                new IncidentAccessPolicy(), Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static <T extends BaseEntity> T withId(T entity, Long id) {
        try {
            Field field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Incident inProgress() {
        ITService payment = new ITService("Payment Application", "Payments", (short) 5);
        Incident incident = new Incident("INC" + nextId, "Payment outage", "Cannot pay", reporter, payment, null,
                (short) 3, (short) 3, 10, T0.minus(Duration.ofMinutes(30)));
        incident.classify(Priority.P1, 45, T0, T0.plus(Duration.ofMinutes(30)));
        incident.assignTo(tech);
        incident.acknowledge(T0);
        incident.startProgress();
        withId(incident, nextId);
        stored.put(nextId++, incident);
        return incident;
    }

    private Incident resolved() {
        Incident incident = inProgress();
        incident.resolve("Restarted the gateway", T0);
        return incident;
    }

    private Incident verified() {
        Incident incident = resolved();
        incident.verify();
        return incident;
    }

    private void assertNothingChanged(Incident incident, IncidentStatus status) {
        assertEquals(status, incident.getStatus());
        assertTrue(savedFeedback.isEmpty());
        assertTrue(incidentSaves.isEmpty());
        assertTrue(published.isEmpty());
    }

    // ---------- the happy path ----------

    @Test
    void theReporterRatesAVerifiedIncidentAndItClosesByItself() {
        Incident incident = verified();

        Feedback feedback = service.submitFeedback(incident.getId(), 5, "Fast and friendly, thanks", reporter);

        assertSame(incident, feedback.getIncident());
        assertEquals(5, feedback.getRating());
        assertEquals("Fast and friendly, thanks", feedback.getComments());
        assertEquals(List.of(feedback), savedFeedback);
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
        assertEquals(T0, incident.getClosedAt());
        assertEquals(List.of(incident.getIncidentNumber() + " CLOSED"), incidentSaves);
    }

    @Test
    void closingIsAnnouncedAsDoneByTheSystem() {
        Incident incident = verified();

        service.submitFeedback(incident.getId(), 4, null, reporter);

        assertEquals(1, published.size());
        IncidentEvent event = published.get(0);
        assertEquals(IncidentEventType.CLOSED, event.type());
        assertEquals(IncidentStatus.VERIFIED, event.previous());
        assertEquals(IncidentStatus.CLOSED, event.current());
        assertNull(event.actor());
        assertEquals(T0, event.at());
        assertTrue(event.description().contains("rating 4"), event.description());
    }

    @Test
    void everyRatingFromOneToFiveIsAccepted() {
        for (int rating = 1; rating <= 5; rating++) {
            Incident incident = verified();
            assertEquals(rating, service.submitFeedback(incident.getId(), rating, null, reporter).getRating());
        }
    }

    @Test
    void aMissingOrBlankCommentIsStoredAsNoComment() {
        assertNull(service.submitFeedback(verified().getId(), 3, null, reporter).getComments());
        assertNull(service.submitFeedback(verified().getId(), 3, "   ", reporter).getComments());
        assertEquals("Okay", service.submitFeedback(verified().getId(), 3, "  Okay  ", reporter).getComments());
    }

    // ---------- the rating ----------

    @Test
    void ratingsOutsideOneToFiveAreRefusedAndNothingChanges() {
        Incident incident = verified();

        for (int bad : new int[]{0, 6, -1, 100, 65537}) {      // 65537 would wrap around to 1 if cast to a short
            InvalidInputException refused = assertThrows(InvalidInputException.class,
                    () -> service.submitFeedback(incident.getId(), bad, "x", reporter));
            assertEquals("Rating must be between 1 and 5", refused.getMessage());
        }

        assertNothingChanged(incident, IncidentStatus.VERIFIED);
    }

    // ---------- once only ----------

    @Test
    void aSecondSubmissionIsRefusedAsInvalidInputNotAsAWrongStatus() {
        Incident incident = verified();
        service.submitFeedback(incident.getId(), 5, "Great", reporter);
        feedbackAlreadyExists = true;                           // what the database now says; the incident is CLOSED
        savedFeedback.clear();
        incidentSaves.clear();
        published.clear();

        InvalidInputException refused = assertThrows(InvalidInputException.class,
                () -> service.submitFeedback(incident.getId(), 1, "Changed my mind", reporter));

        assertTrue(refused.getMessage().contains("already"), refused.getMessage());
        assertNothingChanged(incident, IncidentStatus.CLOSED);
    }

    // ---------- status ----------

    @Test
    void feedbackIsRefusedUntilTheFixIsVerified() {
        Incident working = inProgress();
        Incident solved = resolved();

        assertThrows(InvalidStateTransitionException.class, () -> service.submitFeedback(working.getId(), 5, "x", reporter));
        InvalidStateTransitionException refused = assertThrows(InvalidStateTransitionException.class,
                () -> service.submitFeedback(solved.getId(), 5, "x", reporter));

        assertTrue(refused.getMessage().contains("RESOLVED"), refused.getMessage());
        assertEquals(IncidentStatus.IN_PROGRESS, working.getStatus());
        assertEquals(IncidentStatus.RESOLVED, solved.getStatus());
        assertTrue(savedFeedback.isEmpty());
        assertTrue(incidentSaves.isEmpty());
        assertTrue(published.isEmpty());
    }

    @Test
    void aClosedIncidentWithoutFeedbackAlsoRefusesIt() {
        Incident incident = verified();
        incident.close(T0);                                      // an admin closed it without waiting for feedback

        assertThrows(InvalidStateTransitionException.class, () -> service.submitFeedback(incident.getId(), 5, "x", reporter));

        assertNothingChanged(incident, IncidentStatus.CLOSED);
    }

    // ---------- who ----------

    @Test
    void onlyTheReporterCanGiveFeedback() {
        Incident incident = verified();

        assertThrows(UnauthorizedOperationException.class, () -> service.submitFeedback(incident.getId(), 5, "x", tech));
        assertThrows(UnauthorizedOperationException.class, () -> service.submitFeedback(incident.getId(), 5, "x", admin));
        assertThrows(UnauthorizedOperationException.class, () -> service.submitFeedback(incident.getId(), 5, "x", stranger));

        assertNothingChanged(incident, IncidentStatus.VERIFIED);
    }

    @Test
    void aMissingIncidentIsReported() {
        assertThrows(IncidentNotFoundException.class, () -> service.submitFeedback(999L, 5, "x", reporter));

        assertTrue(savedFeedback.isEmpty());
        assertTrue(published.isEmpty());
    }
}
