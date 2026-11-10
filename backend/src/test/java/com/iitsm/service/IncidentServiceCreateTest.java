package com.iitsm.service;

import com.iitsm.config.SlaProperties;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.observer.IncidentObserver;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of the order of the create-incident steps. The collaborators are hand-written fakes
 * (Mockito cannot mock concrete classes on JDK 27); everything that is pure logic (priority, SLA deadlines) is real.
 * Every step writes a line to one shared log, so the order of the steps can be checked.
 */
class IncidentServiceCreateTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    final List<String> log = new ArrayList<>();
    final List<IncidentEvent> published = new ArrayList<>();
    final List<DuplicateCandidate> duplicatesToReturn = new ArrayList<>();
    final Map<String, Integer> settings = new HashMap<>();
    Optional<Technician> technicianToReturn;
    boolean factoryFails;
    long nextId;
    double timeScale;

    Employee reporter;
    Technician technician;
    ITService payment;
    IncidentService service;

    // ---------- fakes ----------

    class FakeFactory extends IncidentFactory {
        FakeFactory() {
            super(null, null, null, Clock.fixed(T0, ZoneOffset.UTC));
        }

        @Override
        public Incident create(CreateIncidentCommand command, User reporter) {
            log.add("factory");
            if (factoryFails) {
                throw new InvalidInputException("Title is required");
            }
            return new Incident("INC1001", command.title(), command.description(), reporter, payment, null,
                    command.impact(), command.urgency(), command.affectedUsers(), T0);
        }
    }

    class FakeSlaPolicies extends SlaPolicyService {
        FakeSlaPolicies() {
            super(null);
        }

        @Override
        public SlaPolicy findPolicy(Priority priority, ITService service) {
            log.add("policy " + priority);
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

        @Override
        public int getInt(String key, int defaultValue) {
            return settings.getOrDefault(key, defaultValue);
        }
    }

    class FakeDuplicates extends DuplicateDetectionService {
        FakeDuplicates() {
            super(null, null, null);
        }

        @Override
        public List<DuplicateCandidate> findCandidates(Incident incident) {
            log.add("duplicates (status " + incident.getStatus() + ", saved " + (incident.getId() != null) + ")");
            return List.copyOf(duplicatesToReturn);
        }
    }

    class FakeAssignment extends AssignmentService {
        FakeAssignment() {
            super(null, null, null, null, List.of());
        }

        @Override
        public Optional<Technician> autoAssign(Incident incident) {
            log.add("assign (status " + incident.getStatus() + ")");
            return technicianToReturn;
        }
    }

    // ---------- setup ----------

    @BeforeEach
    void setUp() {
        timeScale = 1.0;
        technicianToReturn = Optional.empty();
        reporter = new Employee("emp1@iitsm.local", "hash", "Emma Employee", "Finance");
        technician = new Technician("tech1@iitsm.local", "hash", "Tom Technician", null, 5, 4);
        payment = new ITService("Payment Application", "Payments", (short) 5);
        buildService();
    }

    /** Built after the test changes the time scale, because SlaProperties is an immutable record. */
    private void buildService() {
        IncidentRepository repository = (IncidentRepository) Proxy.newProxyInstance(
                IncidentRepository.class.getClassLoader(), new Class<?>[]{IncidentRepository.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("save")) {
                        throw new UnsupportedOperationException("Not expected: " + method.getName());
                    }
                    Incident incident = (Incident) args[0];
                    if (incident.getId() == null) {
                        setId(incident, ++nextId);
                    }
                    log.add("save " + incident.getStatus());
                    return incident;
                });
        IncidentObserver recorder = event -> {
            log.add("event " + event.type());
            published.add(event);
        };
        service = new IncidentService(new FakeFactory(), repository, new ScoreBasedPriorityCalculator(),
                new FakeSlaPolicies(), new SlaEvaluator(), new SlaProperties(timeScale, 0.8, 30000),
                new FakeConfig(), new FakeDuplicates(), new FakeAssignment(),
                new IncidentEventPublisher(List.of(recorder)), new IncidentAccessPolicy(),
                Clock.fixed(T0, ZoneOffset.UTC));
    }

    private static void setId(Incident incident, Long id) {
        try {
            Field field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(incident, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private CreateIncidentCommand command(int impact, int urgency, int affectedUsers) {
        return new CreateIncidentCommand("Payment application outage", "Users cannot complete payment transactions",
                1L, 1L, (short) impact, (short) urgency, affectedUsers);
    }

    private List<IncidentEventType> eventTypes() {
        return published.stream().map(IncidentEvent::type).toList();
    }

    // ---------- the happy path ----------

    @Test
    void aPaymentOutageIsSavedClassifiedAsP1AndAssigned() {
        technicianToReturn = Optional.of(technician);

        CreateIncidentResult result = service.create(command(3, 3, 250), reporter);
        Incident incident = result.incident();

        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
        assertEquals(Priority.P1, incident.getPriority());
        assertEquals(45, incident.getPriorityScore().intValue());                     // 3 x 3 x 5
        assertEquals(T0.plus(Duration.ofMinutes(5)), incident.getResponseDeadline());
        assertEquals(T0.plus(Duration.ofMinutes(30)), incident.getResolutionDeadline());
        assertSame(technician, incident.getAssignee());
        assertSame(reporter, incident.getReporter());
    }

    @Test
    void theStepsHappenInTheRightOrder() {
        technicianToReturn = Optional.of(technician);

        service.create(command(3, 3, 250), reporter);

        assertEquals(List.of(
                "factory",
                "save NEW", "event CREATED",
                "policy P1", "save CLASSIFIED", "event CLASSIFIED",
                "duplicates (status CLASSIFIED, saved true)",
                "assign (status CLASSIFIED)",
                "save ASSIGNED", "event ASSIGNED"), log);
    }

    @Test
    void theEventsCarryTheRightStatusesAndActors() {
        technicianToReturn = Optional.of(technician);

        service.create(command(3, 3, 250), reporter);

        assertEquals(List.of(IncidentEventType.CREATED, IncidentEventType.CLASSIFIED, IncidentEventType.ASSIGNED),
                eventTypes());
        IncidentEvent created = published.get(0);
        IncidentEvent classified = published.get(1);
        IncidentEvent assigned = published.get(2);

        assertNull(created.previous());
        assertEquals(IncidentStatus.NEW, created.current());
        assertSame(reporter, created.actor());
        assertEquals(T0, created.at());

        assertEquals(IncidentStatus.NEW, classified.previous());
        assertEquals(IncidentStatus.CLASSIFIED, classified.current());
        assertNull(classified.actor());                                       // done by the system

        assertEquals(IncidentStatus.CLASSIFIED, assigned.previous());
        assertEquals(IncidentStatus.ASSIGNED, assigned.current());
        assertNull(assigned.actor());
        assertTrue(assigned.description().contains("Tom Technician"), assigned.description());
        assertTrue(assigned.explicitRecipientIds().isEmpty());
    }

    // ---------- nobody to assign ----------

    @Test
    void whenNobodyIsEligibleTheIncidentStaysClassifiedAndAssignmentFailedIsAnnounced() {
        technicianToReturn = Optional.empty();

        CreateIncidentResult result = service.create(command(3, 3, 250), reporter);

        assertEquals(IncidentStatus.CLASSIFIED, result.incident().getStatus());
        assertNull(result.incident().getAssignee());
        assertEquals(List.of(IncidentEventType.CREATED, IncidentEventType.CLASSIFIED,
                IncidentEventType.ASSIGNMENT_FAILED), eventTypes());
        IncidentEvent failed = published.get(2);
        assertEquals(IncidentStatus.CLASSIFIED, failed.previous());
        assertEquals(IncidentStatus.CLASSIFIED, failed.current());
        assertNull(failed.actor());
        assertFalse(log.contains("save ASSIGNED"));
    }

    // ---------- duplicates ----------

    @Test
    void possibleDuplicatesAreReturnedButDoNotStopTheIncident() {
        technicianToReturn = Optional.of(technician);
        duplicatesToReturn.add(new DuplicateCandidate(42L, "INC1000", "Payment app down", 0.7));

        CreateIncidentResult result = service.create(command(3, 3, 250), reporter);

        assertEquals(1, result.possibleDuplicates().size());
        assertEquals("INC1000", result.possibleDuplicates().get(0).number());
        assertEquals(IncidentStatus.ASSIGNED, result.incident().getStatus());   // still created and assigned
    }

    @Test
    void noDuplicatesGivesAnEmptyList() {
        assertTrue(service.create(command(3, 3, 250), reporter).possibleDuplicates().isEmpty());
    }

    // ---------- priority and SLA inputs ----------

    @Test
    void manyAffectedUsersBumpThePriorityUsingTheSetting() {
        // 2 x 2 x 5 = 20 is P2; at the bump threshold it becomes P1, one user below it stays P2
        assertEquals(Priority.P2, service.create(command(2, 2, 99), reporter).incident().getPriority());
        assertEquals(Priority.P1, service.create(command(2, 2, 100), reporter).incident().getPriority());

        settings.put(ConfigKeys.PRIORITY_BUMP_USERS, 50);                       // the setting is read on every call
        assertEquals(Priority.P1, service.create(command(2, 2, 50), reporter).incident().getPriority());
    }

    @Test
    void theSlaPolicyOfTheCalculatedPriorityIsUsed() {
        Incident p2 = service.create(command(2, 2, 1), reporter).incident();     // score 20 -> P2: 15 and 120 minutes

        assertEquals(T0.plus(Duration.ofMinutes(15)), p2.getResponseDeadline());
        assertEquals(T0.plus(Duration.ofMinutes(120)), p2.getResolutionDeadline());
    }

    @Test
    void theTimeScaleShrinksTheDeadlines() {
        timeScale = 10.0;
        buildService();

        Incident incident = service.create(command(3, 3, 250), reporter).incident();   // P1: 5 and 30 minutes, 10x faster

        assertEquals(T0.plus(Duration.ofSeconds(30)), incident.getResponseDeadline());
        assertEquals(T0.plus(Duration.ofSeconds(180)), incident.getResolutionDeadline());
    }

    // ---------- failures ----------

    @Test
    void anInvalidReportCreatesNothingAndAnnouncesNothing() {
        factoryFails = true;

        assertThrows(InvalidInputException.class, () -> service.create(command(3, 3, 250), reporter));

        assertEquals(List.of("factory"), log);       // no save, no event, no later step
        assertTrue(published.isEmpty());
    }

    @Test
    void aDeactivatedUserCannotReportAndNothingHappens() {
        reporter.deactivate();

        assertThrows(UnauthorizedOperationException.class, () -> service.create(command(3, 3, 250), reporter));

        assertTrue(log.isEmpty());
    }

    @Test
    void aMissingReporterIsRejected() {
        assertThrows(NullPointerException.class, () -> service.create(command(3, 3, 250), null));
    }
}
