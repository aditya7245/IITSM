package com.iitsm.service;

import com.iitsm.config.SlaProperties;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.ITManager;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.exception.IncidentNotFoundException;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.InvalidStateTransitionException;
import com.iitsm.exception.TechnicianUnavailableException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.security.IncidentAccessPolicy;
import com.iitsm.strategy.ScoreBasedPriorityCalculator;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of the lifecycle actions of {@link IncidentService}: who may do what, which rules apply and
 * which event is announced. Fakes are hand-written (Mockito cannot mock concrete classes on JDK 27); the incident
 * state machine, SLA calculation and access policy are the real ones.
 */
class IncidentServiceLifecycleTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant CREATED = T0.minus(Duration.ofMinutes(10));

    final Map<Long, Incident> stored = new HashMap<>();
    final List<String> saves = new ArrayList<>();
    final List<IncidentEvent> published = new ArrayList<>();
    final Map<Long, Technician> technicians = new HashMap<>();

    Employee reporter;
    Employee stranger;
    Technician tech;
    Technician otherTech;
    Administrator admin;
    ITManager manager;
    ITService payment;
    IncidentService service;
    long nextId = 100;

    // ---------- fakes ----------

    class FakeFactory extends IncidentFactory {
        FakeFactory() {
            super(null, null, null, Clock.fixed(T0, ZoneOffset.UTC));
        }
    }

    class FakeSlaPolicies extends SlaPolicyService {
        FakeSlaPolicies() {
            super(null);
        }

        @Override
        public SlaPolicy findPolicy(Priority priority, ITService service) {
            return switch (priority) {
                case P1 -> new SlaPolicy(priority, null, 5, 30);
                case P2 -> new SlaPolicy(priority, null, 15, 120);
                case P3 -> new SlaPolicy(priority, null, 60, 480);
                case P4 -> new SlaPolicy(priority, null, 240, 1440);
            };
        }
    }

    class FakeConfig extends ConfigService {
        FakeConfig() {
            super(null);
        }
    }

    class FakeDuplicates extends DuplicateDetectionService {
        FakeDuplicates() {
            super(null, null, null);
        }
    }

    class FakeAssignment extends AssignmentService {
        FakeAssignment() {
            super(null, null, null, null, List.of());
        }

        @Override
        public Technician assignManually(Incident incident, Long technicianId) {
            Technician technician = technicians.get(technicianId);
            if (technician == null) {
                throw new TechnicianUnavailableException("Technician " + technicianId + " is not available");
            }
            return technician;
        }
    }

    // ---------- setup ----------

    @BeforeEach
    void setUp() {
        reporter = withId(new Employee("emp1@iitsm.local", "h", "Emma Employee", "Finance"), 1L);
        stranger = withId(new Employee("emp2@iitsm.local", "h", "Sam Stranger", "HR"), 2L);
        tech = withId(new Technician("tech1@iitsm.local", "h", "Tom Technician", null, 5, 4), 3L);
        otherTech = withId(new Technician("tech2@iitsm.local", "h", "Olga Other", null, 5, 2), 4L);
        admin = withId(new Administrator("admin@iitsm.local", "h", "Ada Admin"), 5L);
        manager = withId(new ITManager("manager@iitsm.local", "h", "Max Manager"), 6L);
        technicians.put(3L, tech);
        technicians.put(4L, otherTech);
        payment = new ITService("Payment Application", "Payments", (short) 5);

        IncidentRepository repository = (IncidentRepository) Proxy.newProxyInstance(
                IncidentRepository.class.getClassLoader(), new Class<?>[]{IncidentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findById" -> Optional.ofNullable(stored.get((Long) args[0]));
                    case "save" -> {
                        Incident incident = (Incident) args[0];
                        saves.add(incident.getIncidentNumber() + " " + incident.getStatus());
                        yield incident;
                    }
                    default -> throw new UnsupportedOperationException("Not expected: " + method.getName());
                });
        service = new IncidentService(new FakeFactory(), repository, new ScoreBasedPriorityCalculator(),
                new FakeSlaPolicies(), new SlaEvaluator(), new SlaProperties(1.0, 0.8, 30000),
                new FakeConfig(), new FakeDuplicates(), new FakeAssignment(),
                new IncidentEventPublisher(List.of(published::add)), new IncidentAccessPolicy(),
                Clock.fixed(T0, ZoneOffset.UTC));
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

    /** A P1 incident reported by {@code reporter}, in CLASSIFIED status. */
    private Incident classified() {
        Incident incident = new Incident("INC" + nextId, "Payment outage", "Cannot pay", reporter, payment, null,
                (short) 3, (short) 3, 10, CREATED);
        incident.classify(Priority.P1, 45, CREATED.plus(Duration.ofMinutes(5)), CREATED.plus(Duration.ofMinutes(30)));
        withId(incident, nextId);
        stored.put(nextId++, incident);
        return incident;
    }

    private Incident assigned() {
        Incident incident = classified();
        incident.assignTo(tech);
        return incident;
    }

    private Incident acknowledged() {
        Incident incident = assigned();
        incident.acknowledge(T0);
        return incident;
    }

    private Incident inProgress() {
        Incident incident = acknowledged();
        incident.startProgress();
        return incident;
    }

    private Incident resolved() {
        Incident incident = inProgress();
        incident.resolve("Restarted the payment gateway", T0);
        return incident;
    }

    private Incident verified() {
        Incident incident = resolved();
        incident.verify();
        return incident;
    }

    private IncidentEvent onlyEvent() {
        assertEquals(1, published.size());
        return published.get(0);
    }

    private void assertAnnounced(IncidentEventType type, IncidentStatus previous, IncidentStatus current, User actor) {
        IncidentEvent event = onlyEvent();
        assertEquals(type, event.type());
        assertEquals(previous, event.previous());
        assertEquals(current, event.current());
        assertSame(actor, event.actor());
        assertEquals(T0, event.at());
        assertNotNull(event.description());
    }

    // ---------- the happy paths ----------

    @Test
    void theAssigneeAcknowledgesAndStartsWorking() {
        Incident incident = assigned();

        service.acknowledge(incident.getId(), tech);
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());
        assertAnnounced(IncidentEventType.ACKNOWLEDGED, IncidentStatus.ASSIGNED, IncidentStatus.ACKNOWLEDGED, tech);

        published.clear();
        service.startProgress(incident.getId(), tech);
        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertAnnounced(IncidentEventType.INVESTIGATION_STARTED, IncidentStatus.ACKNOWLEDGED, IncidentStatus.IN_PROGRESS, tech);
        assertEquals(2, saves.size());
    }

    @Test
    void theAssigneeResolvesWithTheFixDescription() {
        Incident incident = inProgress();

        Incident result = service.resolve(incident.getId(), "Restarted the payment gateway", tech);

        assertSame(incident, result);
        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertEquals("Restarted the payment gateway", incident.getResolution());
        assertEquals(T0, incident.getResolvedAt());
        assertAnnounced(IncidentEventType.RESOLVED, IncidentStatus.IN_PROGRESS, IncidentStatus.RESOLVED, tech);
        assertTrue(published.get(0).description().contains("Restarted the payment gateway"));
    }

    @Test
    void aVeryLongResolutionIsShortenedInTheAnnouncementButKeptInFull() {
        Incident incident = inProgress();
        String longText = "fixed ".repeat(200);

        service.resolve(incident.getId(), longText, tech);

        assertEquals(longText, incident.getResolution());
        assertTrue(onlyEvent().description().length() < 260, "length " + onlyEvent().description().length());
    }

    @Test
    void theReporterVerifiesAndAnAdminCloses() {
        Incident incident = resolved();

        service.verify(incident.getId(), reporter);
        assertEquals(IncidentStatus.VERIFIED, incident.getStatus());
        assertAnnounced(IncidentEventType.VERIFIED, IncidentStatus.RESOLVED, IncidentStatus.VERIFIED, reporter);

        published.clear();
        service.close(incident.getId(), admin);
        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
        assertEquals(T0, incident.getClosedAt());
        assertAnnounced(IncidentEventType.CLOSED, IncidentStatus.VERIFIED, IncidentStatus.CLOSED, admin);
    }

    @Test
    void anITManagerCountsAsAnAdmin() {
        Incident incident = verified();

        service.close(incident.getId(), manager);

        assertEquals(IncidentStatus.CLOSED, incident.getStatus());
    }

    @Test
    void theReporterReopensAndTheIncidentIsBackInProgress() {
        Incident incident = resolved();

        service.reopen(incident.getId(), "Still failing for some users", reporter);

        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertNull(incident.getResolution());
        assertAnnounced(IncidentEventType.REOPENED, IncidentStatus.RESOLVED, IncidentStatus.IN_PROGRESS, reporter);
        assertTrue(published.get(0).description().contains("Still failing for some users"));
    }

    @Test
    void reopeningNeedsAReasonAndChangesNothingWithout() {
        Incident incident = resolved();

        assertThrows(InvalidInputException.class, () -> service.reopen(incident.getId(), "   ", reporter));
        assertThrows(InvalidInputException.class, () -> service.reopen(incident.getId(), null, reporter));

        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertTrue(published.isEmpty());
    }

    @Test
    void theReporterOrAnAdminCanCancelBeforeWorkStarts() {
        Incident byReporter = assigned();
        service.cancel(byReporter.getId(), reporter);
        assertEquals(IncidentStatus.CANCELLED, byReporter.getStatus());
        assertAnnounced(IncidentEventType.CANCELLED, IncidentStatus.ASSIGNED, IncidentStatus.CANCELLED, reporter);

        published.clear();
        Incident byAdmin = classified();
        service.cancel(byAdmin.getId(), admin);
        assertEquals(IncidentStatus.CANCELLED, byAdmin.getStatus());
        assertAnnounced(IncidentEventType.CANCELLED, IncidentStatus.CLASSIFIED, IncidentStatus.CANCELLED, admin);
    }

    // ---------- duplicates ----------

    @Test
    void anAdminMarksAnIncidentAsDuplicateOfAnother() {
        Incident original = assigned();
        Incident copy = classified();

        service.markDuplicate(copy.getId(), original.getId(), admin);

        assertEquals(IncidentStatus.DUPLICATE, copy.getStatus());
        assertSame(original, copy.getDuplicateOf());
        assertAnnounced(IncidentEventType.MARKED_DUPLICATE, IncidentStatus.CLASSIFIED, IncidentStatus.DUPLICATE, admin);
        assertTrue(published.get(0).description().contains(original.getIncidentNumber()));
    }

    @Test
    void markingDuplicateNeedsAnExistingDifferentOriginal() {
        Incident incident = classified();

        assertThrows(IncidentNotFoundException.class, () -> service.markDuplicate(incident.getId(), 999L, admin));
        assertThrows(InvalidInputException.class, () -> service.markDuplicate(incident.getId(), null, admin));
        assertThrows(InvalidInputException.class, () -> service.markDuplicate(incident.getId(), incident.getId(), admin));

        assertEquals(IncidentStatus.CLASSIFIED, incident.getStatus());
        assertNull(incident.getDuplicateOf());
        assertTrue(published.isEmpty());
    }

    @Test
    void duplicateIsNotAllowedOnceWorkIsUnderway() {
        Incident original = classified();
        Incident busy = inProgress();

        assertThrows(InvalidStateTransitionException.class, () -> service.markDuplicate(busy.getId(), original.getId(), admin));

        assertEquals(IncidentStatus.IN_PROGRESS, busy.getStatus());
        assertTrue(published.isEmpty());
    }

    // ---------- reassign ----------

    @Test
    void anAdminReassignsAnAssignedIncident() {
        Incident incident = assigned();

        service.reassign(incident.getId(), 4L, admin);

        assertSame(otherTech, incident.getAssignee());
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertAnnounced(IncidentEventType.ASSIGNED, IncidentStatus.ASSIGNED, IncidentStatus.ASSIGNED, admin);
        assertTrue(published.get(0).description().startsWith("Reassigned to Olga Other"), published.get(0).description());
    }

    @Test
    void anAdminCanAssignAnIncidentThatNobodyCouldBeAutoAssigned() {
        Incident incident = classified();

        service.reassign(incident.getId(), 3L, admin);

        assertSame(tech, incident.getAssignee());
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertAnnounced(IncidentEventType.ASSIGNED, IncidentStatus.CLASSIFIED, IncidentStatus.ASSIGNED, admin);
        assertTrue(published.get(0).description().startsWith("Assigned to Tom Technician"), published.get(0).description());
    }

    @Test
    void reassignmentIsRefusedOnceTheIncidentIsAcknowledged() {
        Incident incident = acknowledged();

        assertThrows(InvalidStateTransitionException.class, () -> service.reassign(incident.getId(), 4L, admin));

        assertSame(tech, incident.getAssignee());
        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());
        assertTrue(published.isEmpty());
    }

    @Test
    void reassigningToTheCurrentAssigneeIsRefused() {
        Incident incident = assigned();

        assertThrows(InvalidInputException.class, () -> service.reassign(incident.getId(), 3L, admin));

        assertTrue(published.isEmpty());
    }

    @Test
    void anUnavailableTechnicianLeavesTheIncidentAsItWas() {
        Incident incident = assigned();

        assertThrows(TechnicianUnavailableException.class, () -> service.reassign(incident.getId(), 999L, admin));

        assertSame(tech, incident.getAssignee());
        assertTrue(published.isEmpty());
        assertTrue(saves.isEmpty());
    }

    // ---------- priority ----------

    @Test
    void changingThePriorityRecomputesTheDeadlinesFromCreationAndKeepsThePausedTime() {
        Incident incident = inProgress();
        incident.requestInfo(T0);
        incident.receiveInfo(T0.plus(Duration.ofMinutes(10)));          // 600 seconds paused so far
        assertEquals(600, incident.getSlaPausedSeconds());

        service.changePriority(incident.getId(), Priority.P3, admin);

        assertEquals(Priority.P3, incident.getPriority());
        assertEquals(45, incident.getPriorityScore().intValue());        // the calculated score is kept
        assertEquals(CREATED.plus(Duration.ofMinutes(60)).plusSeconds(600), incident.getResponseDeadline());
        assertEquals(CREATED.plus(Duration.ofMinutes(480)).plusSeconds(600), incident.getResolutionDeadline());
        assertAnnounced(IncidentEventType.PRIORITY_CHANGED, IncidentStatus.IN_PROGRESS, IncidentStatus.IN_PROGRESS, admin);
        assertTrue(published.get(0).description().contains("P1 to P3"), published.get(0).description());
    }

    @Test
    void thePriorityCannotChangeOnceResolvedAndNothingIsAnnounced() {
        Incident incident = resolved();
        Instant before = incident.getResolutionDeadline();

        assertThrows(InvalidInputException.class, () -> service.changePriority(incident.getId(), Priority.P4, admin));

        assertEquals(Priority.P1, incident.getPriority());
        assertEquals(before, incident.getResolutionDeadline());
        assertTrue(published.isEmpty());
    }

    @Test
    void changingThePriorityNeedsAPriority() {
        Incident incident = classified();

        assertThrows(InvalidInputException.class, () -> service.changePriority(incident.getId(), null, admin));
    }

    // ---------- who may do what ----------

    @Test
    void everyActionRefusesTheWrongPersonAndChangesNothing() {
        Incident incident = resolved();      // any status: the check on who is asking comes first
        Long id = incident.getId();
        Long other = classified().getId();
        published.clear();

        List<Supplier<Incident>> notAllowedForTheStranger = List.of(
                () -> service.acknowledge(id, stranger),
                () -> service.startProgress(id, stranger),
                () -> service.resolve(id, "fix", stranger),
                () -> service.verify(id, stranger),
                () -> service.reopen(id, "why", stranger),
                () -> service.cancel(id, stranger),
                () -> service.close(id, stranger),
                () -> service.markDuplicate(id, other, stranger),
                () -> service.reassign(id, 4L, stranger),
                () -> service.changePriority(id, Priority.P2, stranger));
        for (Supplier<Incident> action : notAllowedForTheStranger) {
            assertThrows(UnauthorizedOperationException.class, action::get);
        }

        // the reporter is not the assignee, and the assignee is not the reporter
        assertThrows(UnauthorizedOperationException.class, () -> service.resolve(id, "fix", reporter));
        assertThrows(UnauthorizedOperationException.class, () -> service.verify(id, tech));
        assertThrows(UnauthorizedOperationException.class, () -> service.reopen(id, "why", tech));
        // only admins close, reassign, mark duplicates or change the priority
        assertThrows(UnauthorizedOperationException.class, () -> service.close(id, reporter));
        assertThrows(UnauthorizedOperationException.class, () -> service.reassign(id, 4L, reporter));
        assertThrows(UnauthorizedOperationException.class, () -> service.markDuplicate(id, other, tech));
        assertThrows(UnauthorizedOperationException.class, () -> service.changePriority(id, Priority.P2, tech));

        assertEquals(IncidentStatus.RESOLVED, incident.getStatus());
        assertTrue(published.isEmpty());
        assertTrue(saves.isEmpty());
    }

    @Test
    void everyActionReportsAMissingIncident() {
        List<Supplier<Incident>> actions = List.of(
                () -> service.acknowledge(999L, tech),
                () -> service.startProgress(999L, tech),
                () -> service.resolve(999L, "fix", tech),
                () -> service.verify(999L, reporter),
                () -> service.reopen(999L, "why", reporter),
                () -> service.cancel(999L, reporter),
                () -> service.close(999L, admin),
                () -> service.markDuplicate(999L, 1L, admin),
                () -> service.reassign(999L, 4L, admin),
                () -> service.changePriority(999L, Priority.P2, admin));
        for (Supplier<Incident> action : actions) {
            assertThrows(IncidentNotFoundException.class, action::get);
        }
        assertTrue(published.isEmpty());
    }

    // ---------- lifecycle rules ----------

    @Test
    void anActionThatIsNotAllowedInTheCurrentStatusChangesNothing() {
        Incident incident = acknowledged();

        assertThrows(InvalidStateTransitionException.class, () -> service.acknowledge(incident.getId(), tech));
        assertThrows(InvalidStateTransitionException.class, () -> service.resolve(incident.getId(), "fix", tech));
        assertThrows(InvalidStateTransitionException.class, () -> service.cancel(incident.getId(), reporter));
        assertThrows(InvalidStateTransitionException.class, () -> service.close(incident.getId(), admin));

        assertEquals(IncidentStatus.ACKNOWLEDGED, incident.getStatus());
        assertTrue(published.isEmpty());
        assertTrue(saves.isEmpty());
    }

    @Test
    void aBlankResolutionIsRefused() {
        Incident incident = inProgress();

        assertThrows(InvalidInputException.class, () -> service.resolve(incident.getId(), " ", tech));

        assertEquals(IncidentStatus.IN_PROGRESS, incident.getStatus());
        assertTrue(published.isEmpty());
    }
}
