package com.iitsm.service;

import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.CommentType;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentComment;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.TeamLead;
import com.iitsm.domain.user.User;
import com.iitsm.exception.IncidentNotFoundException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.IncidentCommentRepository;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.security.IncidentAccessPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of {@link IncidentCommentService}: who may write and read what, the pause and resume of the
 * SLA clock, and which events are announced. The repositories are Proxies answering only the calls the service
 * may make; the incident rules and the access policy are the real ones.
 */
class IncidentCommentServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant CREATED = T0.minus(Duration.ofMinutes(10));
    private static final Instant RESPONSE_DEADLINE = CREATED.plus(Duration.ofMinutes(5));
    private static final Instant RESOLUTION_DEADLINE = CREATED.plus(Duration.ofMinutes(30));

    /** A clock the test can move forward. */
    static class TestClock extends Clock {
        Instant now = T0;

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    final Map<Long, Incident> stored = new HashMap<>();
    final List<IncidentComment> savedComments = new ArrayList<>();
    final List<IncidentComment> threadInDatabase = new ArrayList<>();
    final List<String> incidentSaves = new ArrayList<>();
    final List<IncidentEvent> published = new ArrayList<>();
    final TestClock clock = new TestClock();

    Employee reporter;
    Employee stranger;
    Technician tech;
    Technician otherTech;
    TeamLead lead;
    Administrator admin;
    IncidentCommentService service;
    long nextId = 100;

    @BeforeEach
    void setUp() {
        reporter = withId(new Employee("emp1@iitsm.local", "h", "Emma Employee", "Finance"), 1L);
        stranger = withId(new Employee("emp2@iitsm.local", "h", "Sam Stranger", "HR"), 2L);
        Team team = new Team("Application Support");
        lead = withId(new TeamLead("lead@iitsm.local", "h", "Lena Lead", team, 5, 8), 3L);
        team.assignLead(lead);
        tech = withId(new Technician("tech1@iitsm.local", "h", "Tom Technician", team, 5, 4), 4L);
        otherTech = withId(new Technician("tech2@iitsm.local", "h", "Olga Other", new Team("Infra"), 5, 2), 5L);
        admin = withId(new Administrator("admin@iitsm.local", "h", "Ada Admin"), 6L);

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
        IncidentCommentRepository comments = (IncidentCommentRepository) Proxy.newProxyInstance(
                IncidentCommentRepository.class.getClassLoader(), new Class<?>[]{IncidentCommentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "save" -> {
                        savedComments.add((IncidentComment) args[0]);
                        yield args[0];
                    }
                    case "findByIncidentIdOrderByCreatedAtAscIdAsc" -> List.copyOf(threadInDatabase);
                    default -> throw new UnsupportedOperationException("Not expected: " + method.getName());
                });
        service = new IncidentCommentService(incidents, comments, new IncidentEventPublisher(List.of(published::add)),
                new IncidentAccessPolicy(), clock);
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

    /** A P1 incident assigned to {@code tech}, deadlines 5 and 30 minutes after creation. */
    private Incident assigned() {
        ITService payment = new ITService("Payment Application", "Payments", (short) 5);
        Incident incident = new Incident("INC" + nextId, "Payment outage", "Cannot pay", reporter, payment, null,
                (short) 3, (short) 3, 10, CREATED);
        incident.classify(Priority.P1, 45, RESPONSE_DEADLINE, RESOLUTION_DEADLINE);
        incident.assignTo(tech);
        withId(incident, nextId);
        stored.put(nextId++, incident);
        return incident;
    }

    private Incident inProgress() {
        Incident incident = assigned();
        incident.acknowledge(T0);
        incident.startProgress();
        return incident;
    }

    private Incident pendingInfo() {
        Incident incident = inProgress();
        incident.requestInfo(T0);
        return incident;
    }

    private Incident resolved() {
        Incident incident = inProgress();
        incident.resolve("Restarted the gateway", T0);
        return incident;
    }

    private Incident closed() {
        Incident incident = resolved();
        incident.verify();
        incident.close(T0);
        return incident;
    }

    private IncidentComment comment(Incident incident, User author, CommentType type, String body) {
        return new IncidentComment(incident, author, type, body);
    }

    private void assertNothingChanged() {
        assertTrue(savedComments.isEmpty());
        assertTrue(incidentSaves.isEmpty());
        assertTrue(published.isEmpty());
    }

    // ---------- notes ----------

    @Test
    void theAssigneeAndAnAdminCanAddInternalNotes() {
        Incident incident = inProgress();

        IncidentComment byTech = service.addNote(incident.getId(), "Looks like the gateway", tech);
        IncidentComment byAdmin = service.addNote(incident.getId(), "Escalating to the vendor", admin);

        assertEquals(CommentType.NOTE, byTech.getType());
        assertSame(tech, byTech.getAuthor());
        assertSame(incident, byTech.getIncident());
        assertEquals("Looks like the gateway", byTech.getBody());
        assertSame(admin, byAdmin.getAuthor());
        assertEquals(List.of(byTech, byAdmin), savedComments);
        assertTrue(published.isEmpty());                         // a note does not change the incident
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
    }

    @Test
    void theReporterOthersAndOtherTechniciansCannotAddNotes() {
        Incident incident = inProgress();

        assertThrows(UnauthorizedOperationException.class, () -> service.addNote(incident.getId(), "x", reporter));
        assertThrows(UnauthorizedOperationException.class, () -> service.addNote(incident.getId(), "x", stranger));
        assertThrows(UnauthorizedOperationException.class, () -> service.addNote(incident.getId(), "x", otherTech));

        assertNothingChanged();
    }

    @Test
    void aBlankNoteIsRefused() {
        Incident incident = inProgress();

        assertThrows(InvalidInputException.class, () -> service.addNote(incident.getId(), "  ", tech));

        assertNothingChanged();
    }

    // ---------- asking for information ----------

    @Test
    void theAssigneeRequestsInfoTheIncidentWaitsAndTheClockPauses() {
        Incident incident = inProgress();

        IncidentComment comment = service.requestInfo(incident.getId(), "Which users are affected?", tech);

        assertEquals(IncidentStatus.PENDING_INFO, incident.getStatus());
        assertEquals(T0, incident.getSlaPausedAt());
        assertEquals(CommentType.INFO_REQUEST, comment.getType());
        assertSame(tech, comment.getAuthor());
        assertEquals(List.of(comment), savedComments);
        assertEquals(1, published.size());
        IncidentEvent event = published.get(0);
        assertEquals(IncidentEventType.INFO_REQUESTED, event.type());
        assertEquals(IncidentStatus.IN_PROGRESS, event.previous());
        assertEquals(IncidentStatus.PENDING_INFO, event.current());
        assertSame(tech, event.actor());
        assertEquals(T0, event.at());
        assertTrue(event.description().contains("Which users are affected?"), event.description());
    }

    @Test
    void onlyTheAssigneeCanRequestInfo() {
        Incident incident = inProgress();

        assertThrows(UnauthorizedOperationException.class, () -> service.requestInfo(incident.getId(), "x", reporter));
        assertThrows(UnauthorizedOperationException.class, () -> service.requestInfo(incident.getId(), "x", otherTech));
        assertThrows(UnauthorizedOperationException.class, () -> service.requestInfo(incident.getId(), "x", admin));

        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNothingChanged();
    }

    @Test
    void aBlankRequestLeavesTheIncidentInProgressAndTheClockRunning() {
        Incident incident = inProgress();

        assertThrows(InvalidInputException.class, () -> service.requestInfo(incident.getId(), " ", tech));

        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getSlaPausedAt());
        assertNothingChanged();
    }

    @Test
    void infoCanOnlyBeRequestedWhileTheIncidentIsInProgress() {
        Incident acknowledged = assigned();
        acknowledged.acknowledge(T0);
        Incident waiting = pendingInfo();

        assertThrows(InvalidStateTransitionException.class, () -> service.requestInfo(acknowledged.getId(), "Why?", tech));
        assertThrows(InvalidStateTransitionException.class, () -> service.requestInfo(waiting.getId(), "Again?", tech));

        assertEquals(IncidentStatus.ACKNOWLEDGED, acknowledged.getStatus());
        assertNothingChanged();
    }

    // ---------- answering ----------

    @Test
    void theAnswerResumesTheClockAndMovesBothDeadlinesLaterByTheWaitingTime() {
        Incident incident = inProgress();
        service.requestInfo(incident.getId(), "Which users are affected?", tech);
        savedComments.clear();
        published.clear();
        clock.now = T0.plus(Duration.ofMinutes(15));

        IncidentComment answer = service.respondToInfo(incident.getId(), "Everyone in Finance", reporter);

        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getSlaPausedAt());
        assertEquals(900, incident.getSlaPausedSeconds());
        assertEquals(RESPONSE_DEADLINE.plus(Duration.ofMinutes(15)), incident.getResponseDeadline());
        assertEquals(RESOLUTION_DEADLINE.plus(Duration.ofMinutes(15)), incident.getResolutionDeadline());
        assertEquals(CommentType.INFO_RESPONSE, answer.getType());
        assertSame(reporter, answer.getAuthor());
        assertEquals(List.of(answer), savedComments);
        assertEquals(1, published.size());
        IncidentEvent event = published.get(0);
        assertEquals(IncidentEventType.INFO_RECEIVED, event.type());
        assertEquals(IncidentStatus.PENDING_INFO, event.previous());
        assertEquals(IncidentStatus.IN_PROGRESS, event.current());
        assertSame(reporter, event.actor());
        assertTrue(event.description().contains("Everyone in Finance"), event.description());
    }

    @Test
    void anAnswerInAnyOtherUnfinishedStatusIsJustSavedAsAComment() {
        Incident working = inProgress();
        Incident solved = resolved();

        service.respondToInfo(working.getId(), "Extra detail", reporter);
        service.respondToInfo(solved.getId(), "Still seeing it", reporter);

        assertEquals(2, savedComments.size());
        assertEquals(CommentType.INFO_RESPONSE, savedComments.get(0).getType());
        assertTrue(published.isEmpty());
        assertTrue(incidentSaves.isEmpty());
        assertEquals(IncidentStatus.IN_PROGRESS, working.getStatus());
        assertEquals(IncidentStatus.RESOLVED, solved.getStatus());
        assertEquals(RESOLUTION_DEADLINE, working.getResolutionDeadline());      // deadlines untouched
    }

    @Test
    void aFinishedIncidentRefusesComments() {
        Incident done = closed();
        Incident cancelled = assigned();
        cancelled.cancel();

        assertThrows(InvalidStateTransitionException.class, () -> service.respondToInfo(done.getId(), "Thanks", reporter));
        assertThrows(InvalidStateTransitionException.class, () -> service.respondToInfo(cancelled.getId(), "Hello", reporter));
        assertThrows(InvalidStateTransitionException.class, () -> service.addNote(done.getId(), "Late note", admin));

        assertNothingChanged();
    }

    @Test
    void onlyTheReporterCanAnswer() {
        Incident incident = pendingInfo();

        assertThrows(UnauthorizedOperationException.class, () -> service.respondToInfo(incident.getId(), "x", tech));
        assertThrows(UnauthorizedOperationException.class, () -> service.respondToInfo(incident.getId(), "x", admin));
        assertThrows(UnauthorizedOperationException.class, () -> service.respondToInfo(incident.getId(), "x", stranger));

        assertEquals(IncidentStatus.PENDING_INFO, incident.getStatus());
        assertNothingChanged();
    }

    @Test
    void aBlankAnswerKeepsTheIncidentWaiting() {
        Incident incident = pendingInfo();
        clock.now = T0.plus(Duration.ofMinutes(15));

        assertThrows(InvalidInputException.class, () -> service.respondToInfo(incident.getId(), "", reporter));

        assertEquals(IncidentStatus.PENDING_INFO, incident.getStatus());
        assertEquals(T0, incident.getSlaPausedAt());
        assertEquals(RESOLUTION_DEADLINE, incident.getResolutionDeadline());
        assertNothingChanged();
    }

    @Test
    void severalRoundsOfQuestionsAddUpThePausedTime() {
        Incident incident = inProgress();

        service.requestInfo(incident.getId(), "First question?", tech);
        clock.now = T0.plus(Duration.ofMinutes(10));
        service.respondToInfo(incident.getId(), "First answer", reporter);
        service.requestInfo(incident.getId(), "Second question?", tech);
        clock.now = T0.plus(Duration.ofMinutes(25));
        service.respondToInfo(incident.getId(), "Second answer", reporter);

        assertEquals(10 * 60 + 15 * 60, incident.getSlaPausedSeconds());
        assertEquals(RESOLUTION_DEADLINE.plus(Duration.ofMinutes(25)), incident.getResolutionDeadline());
    }

    // ---------- reading ----------

    private void fillThread(Incident incident) {
        threadInDatabase.addAll(List.of(
                comment(incident, tech, CommentType.NOTE, "Internal: checking logs"),
                comment(incident, tech, CommentType.INFO_REQUEST, "Which users?"),
                comment(incident, admin, CommentType.NOTE, "Internal: vendor called"),
                comment(incident, reporter, CommentType.INFO_RESPONSE, "Everyone in Finance")));
    }

    @Test
    void theReporterSeesOnlyTheInformationRequestsAndAnswers() {
        Incident incident = inProgress();
        fillThread(incident);

        List<IncidentComment> seen = service.listComments(incident.getId(), reporter);

        assertEquals(List.of("Which users?", "Everyone in Finance"), seen.stream().map(IncidentComment::getBody).toList());
    }

    @Test
    void staffSeeTheWholeThreadInOrder() {
        Incident incident = inProgress();
        fillThread(incident);

        for (User staff : List.of(tech, lead, admin)) {      // the assignee, the assignee's team lead, an admin
            List<IncidentComment> seen = service.listComments(incident.getId(), staff);
            assertEquals(List.of("Internal: checking logs", "Which users?", "Internal: vendor called", "Everyone in Finance"),
                    seen.stream().map(IncidentComment::getBody).toList());
        }
    }

    @Test
    void peopleWhoMayNotViewTheIncidentCannotReadTheThread() {
        Incident incident = inProgress();
        fillThread(incident);

        assertThrows(UnauthorizedOperationException.class, () -> service.listComments(incident.getId(), stranger));
        assertThrows(UnauthorizedOperationException.class, () -> service.listComments(incident.getId(), otherTech));
    }

    @Test
    void anEmptyThreadGivesAnEmptyList() {
        Incident incident = inProgress();

        assertTrue(service.listComments(incident.getId(), reporter).isEmpty());
        assertTrue(service.listComments(incident.getId(), admin).isEmpty());
    }

    // ---------- missing incident ----------

    @Test
    void everyActionReportsAMissingIncident() {
        List<Supplier<Object>> actions = List.of(
                () -> service.addNote(999L, "x", tech),
                () -> service.requestInfo(999L, "x", tech),
                () -> service.respondToInfo(999L, "x", reporter),
                () -> service.listComments(999L, reporter));
        for (Supplier<Object> action : actions) {
            assertThrows(IncidentNotFoundException.class, action::get);
        }
        assertNothingChanged();
    }
}
