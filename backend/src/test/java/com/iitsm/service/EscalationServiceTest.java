package com.iitsm.service;

import com.iitsm.config.EscalationProperties;
import com.iitsm.config.SlaProperties;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.ITManager;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.TeamLead;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of the escalation chain. Every incident is a P1 created at T0 and assigned to tech1:
 * response deadline at 5 minutes, one level timeout is 5 minutes. The repositories are Proxies, the transaction
 * is a hand-written fake that just runs the work, and the clock is moved by the test.
 */
class EscalationServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");
    private static final Instant RESPONSE_DEADLINE = T0.plus(Duration.ofMinutes(5));

    static class TestClock extends Clock {
        Instant now = T0;

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static class DirectTransactions extends TransactionTemplate {
        @Override
        public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction(null);
        }
    }

    final Map<Long, Incident> stored = new LinkedHashMap<>();
    final List<String> saves = new ArrayList<>();
    final List<IncidentEvent> published = new ArrayList<>();
    final TestClock clock = new TestClock();
    String numberWhoseSaveFails;
    double timeScale = 1.0;

    Team team;
    TeamLead lead;
    Technician tech;
    Administrator admin1;
    Administrator admin2;
    ITManager manager;
    Employee reporter;
    List<User> administrators;
    List<User> managers;
    EscalationService escalation;
    long nextId = 100;

    @BeforeEach
    void setUp() {
        reporter = withId(new Employee("emp1@iitsm.local", "h", "Emma Employee", "Finance"), 1L);
        team = new Team("Application Support");
        lead = withId(new TeamLead("lead@iitsm.local", "h", "Lena Lead", team, 5, 8), 2L);
        team.assignLead(lead);
        tech = withId(new Technician("tech1@iitsm.local", "h", "Tom Technician", team, 5, 4), 3L);
        admin1 = withId(new Administrator("admin@iitsm.local", "h", "Ada Admin"), 4L);
        admin2 = withId(new Administrator("admin2@iitsm.local", "h", "Abe Admin"), 5L);
        manager = withId(new ITManager("manager@iitsm.local", "h", "Max Manager"), 6L);
        administrators = new ArrayList<>(List.of(admin1, admin2));
        managers = new ArrayList<>(List.of(manager));
        buildService();
    }

    private void buildService() {
        IncidentRepository incidents = (IncidentRepository) Proxy.newProxyInstance(
                IncidentRepository.class.getClassLoader(), new Class<?>[]{IncidentRepository.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "findByStatusNotIn" -> stored.values().stream()
                            .filter(i -> !((java.util.Collection<?>) args[0]).contains(i.getStatus())).toList();
                    case "findById" -> Optional.ofNullable(stored.get((Long) args[0]));
                    case "save" -> {
                        Incident incident = (Incident) args[0];
                        if (incident.getIncidentNumber().equals(numberWhoseSaveFails)) {
                            throw new IllegalStateException("database trouble");
                        }
                        saves.add(incident.getIncidentNumber() + " level " + incident.getEscalationLevel());
                        yield incident;
                    }
                    default -> throw new UnsupportedOperationException("Not expected: " + method.getName());
                });
        UserRepository users = (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(), new Class<?>[]{UserRepository.class},
                (proxy, method, args) -> {
                    if (!method.getName().equals("findAllByExactType")) {
                        throw new UnsupportedOperationException("Not expected: " + method.getName());
                    }
                    Class<?> type = (Class<?>) args[0];
                    if (type == Administrator.class) {
                        return administrators;
                    }
                    if (type == ITManager.class) {
                        return managers;
                    }
                    throw new UnsupportedOperationException("Unexpected user type " + type);
                });
        escalation = new EscalationService(incidents, users, new SlaProperties(timeScale, 0.8, 30000),
                new EscalationProperties(5), new IncidentEventPublisher(List.of(published::add)),
                new DirectTransactions(), clock);
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

    private Incident assigned(Priority priority, Technician assignee) {
        ITService payment = new ITService("Payment Application", "Payments", (short) 5);
        Incident incident = new Incident("INC" + nextId, "Payment outage", "Cannot pay", reporter, payment, null,
                (short) 3, (short) 3, 10, T0);
        incident.classify(priority, 45, RESPONSE_DEADLINE, T0.plus(Duration.ofMinutes(30)));
        incident.assignTo(assignee);
        withId(incident, nextId);
        stored.put(nextId++, incident);
        return incident;
    }

    private Incident assignedP1() {
        return assigned(Priority.P1, tech);
    }

    /** Moves the clock to a number of minutes after the response deadline. */
    private void afterDeadline(long minutes, long seconds) {
        clock.now = RESPONSE_DEADLINE.plus(Duration.ofMinutes(minutes)).plusSeconds(seconds);
    }

    private List<Long> recipientsOfLast() {
        return published.get(published.size() - 1).explicitRecipientIds();
    }

    // ---------- the chain ----------

    @Test
    void nothingHappensBeforeTheResponseDeadline() {
        Incident incident = assignedP1();
        clock.now = RESPONSE_DEADLINE.minusSeconds(1);

        assertEquals(0, escalation.runOnce());

        assertEquals(0, incident.getEscalationLevel());
        assertTrue(published.isEmpty());
        assertTrue(saves.isEmpty());
    }

    @Test
    void levelOneAtTheResponseDeadlineAlertsTheTeamLead() {
        Incident incident = assignedP1();
        afterDeadline(0, 0);                                  // exactly at the deadline

        assertEquals(1, escalation.runOnce());

        assertEquals(1, incident.getEscalationLevel());
        assertEquals(1, published.size());
        IncidentEvent event = published.get(0);
        assertEquals(IncidentEventType.ESCALATED, event.type());
        assertEquals(List.of(2L), event.explicitRecipientIds());           // Lena Lead
        assertNull(event.actor());
        assertEquals(IncidentStatus.ASSIGNED, event.previous());
        assertEquals(IncidentStatus.ASSIGNED, event.current());
        assertEquals(clock.now, event.at());
        assertSame(incident, event.incident());
        assertTrue(event.description().contains("level 1 of 3"), event.description());
        assertTrue(event.description().contains("team lead"), event.description());
        assertEquals(List.of("INC100 level 1"), saves);
    }

    @Test
    void levelTwoOneTimeoutLaterAlertsTheAdministratorsAndLevelThreeTheItManagers() {
        Incident incident = assignedP1();

        afterDeadline(0, 0);
        escalation.runOnce();                                 // level 1
        afterDeadline(4, 59);
        assertEquals(0, escalation.runOnce());                // not yet
        afterDeadline(5, 0);
        assertEquals(1, escalation.runOnce());                // level 2
        assertEquals(2, incident.getEscalationLevel());
        assertEquals(List.of(4L, 5L), recipientsOfLast());    // both exact administrators, not the IT manager
        assertTrue(published.get(1).description().contains("level 2 of 3"), published.get(1).description());

        afterDeadline(9, 59);
        assertEquals(0, escalation.runOnce());
        afterDeadline(10, 0);
        assertEquals(1, escalation.runOnce());                // level 3
        assertEquals(3, incident.getEscalationLevel());
        assertEquals(List.of(6L), recipientsOfLast());
        assertTrue(published.get(2).description().contains("level 3 of 3"), published.get(2).description());
        assertTrue(published.get(2).description().contains("IT managers"), published.get(2).description());
    }

    @Test
    void nothingMoreHappensAfterLevelThree() {
        Incident incident = assignedP1();
        for (long minutes : new long[]{0, 5, 10}) {
            afterDeadline(minutes, 0);
            escalation.runOnce();
        }
        published.clear();
        saves.clear();

        afterDeadline(24 * 60, 0);
        assertEquals(0, escalation.runOnce());

        assertEquals(3, incident.getEscalationLevel());
        assertTrue(published.isEmpty());
        assertTrue(saves.isEmpty());
    }

    @Test
    void aVeryLateMonitorStillRaisesOnlyOneLevelPerRun() {
        Incident incident = assignedP1();
        afterDeadline(24 * 60, 0);                            // a day late: all three levels are overdue

        assertEquals(1, escalation.runOnce());
        assertEquals(1, incident.getEscalationLevel());
        assertEquals(1, escalation.runOnce());
        assertEquals(2, incident.getEscalationLevel());
        assertEquals(1, escalation.runOnce());
        assertEquals(3, incident.getEscalationLevel());
        assertEquals(0, escalation.runOnce());
        assertEquals(3, published.size());
    }

    @Test
    void aRepeatRunAtTheSameTimeAddsNothing() {
        assignedP1();
        afterDeadline(0, 0);

        assertEquals(1, escalation.runOnce());
        assertEquals(0, escalation.runOnce());

        assertEquals(1, published.size());
    }

    // ---------- it stops when someone reacts ----------

    @Test
    void escalationStopsOnceTheIncidentIsAcknowledged() {
        Incident incident = assignedP1();
        afterDeadline(0, 0);
        escalation.runOnce();                                 // level 1
        incident.acknowledge(clock.now);
        published.clear();

        afterDeadline(60, 0);

        assertEquals(0, escalation.runOnce());
        assertEquals(1, incident.getEscalationLevel());
        assertTrue(published.isEmpty());
    }

    @Test
    void onlyP1IncidentsAreEscalated() {
        Incident p2 = assigned(Priority.P2, tech);
        Incident p4 = assigned(Priority.P4, tech);
        afterDeadline(60, 0);

        assertEquals(0, escalation.runOnce());

        assertEquals(0, p2.getEscalationLevel());
        assertEquals(0, p4.getEscalationLevel());
        assertTrue(published.isEmpty());
    }

    @Test
    void anIncidentThatIsNotAssignedIsNotEscalated() {
        ITService payment = new ITService("Payment Application", "Payments", (short) 5);
        Incident classified = new Incident("INC1", "t", "d", reporter, payment, null, (short) 3, (short) 3, 1, T0);
        classified.classify(Priority.P1, 45, RESPONSE_DEADLINE, T0.plus(Duration.ofMinutes(30)));
        withId(classified, 1L);
        stored.put(1L, classified);
        Incident inProgress = assignedP1();
        inProgress.acknowledge(T0);
        inProgress.startProgress();
        afterDeadline(60, 0);

        assertEquals(0, escalation.runOnce());

        assertEquals(0, classified.getEscalationLevel());
        assertEquals(0, inProgress.getEscalationLevel());
    }

    @Test
    void escalationNeverReassignsTheIncident() {
        Incident incident = assignedP1();
        for (long minutes : new long[]{0, 5, 10}) {
            afterDeadline(minutes, 0);
            escalation.runOnce();
        }

        assertSame(tech, incident.getAssignee());
        assertEquals(IncidentStatus.ASSIGNED, incident.getStatus());
    }

    // ---------- who is alerted at level 1 ----------

    @Test
    void aTeamWithoutALeadAlertsTheAdministratorsAtLevelOne() {
        Team leaderless = new Team("Infrastructure");
        Technician infra = withId(new Technician("tech3@iitsm.local", "h", "Ivan Infra", leaderless, 5, 2), 7L);
        Incident incident = assigned(Priority.P1, infra);
        afterDeadline(0, 0);

        escalation.runOnce();

        assertEquals(1, incident.getEscalationLevel());
        assertEquals(List.of(4L, 5L), recipientsOfLast());
        assertTrue(published.get(0).description().contains("administrators"), published.get(0).description());
    }

    @Test
    void anAssigneeWithoutATeamAlertsTheAdministratorsAtLevelOne() {
        Technician loner = withId(new Technician("tech4@iitsm.local", "h", "Lone Tech", null, 5, 2), 8L);
        assigned(Priority.P1, loner);
        afterDeadline(0, 0);

        escalation.runOnce();

        assertEquals(List.of(4L, 5L), recipientsOfLast());
    }

    @Test
    void whenTheTeamLeadIsTheAssigneeTheAdministratorsAreAlertedInstead() {
        Incident incident = assigned(Priority.P1, lead);
        afterDeadline(0, 0);

        escalation.runOnce();

        assertEquals(1, incident.getEscalationLevel());
        assertEquals(List.of(4L, 5L), recipientsOfLast());
    }

    @Test
    void aDeactivatedTeamLeadIsSkipped() {
        lead.deactivate();
        assignedP1();
        afterDeadline(0, 0);

        escalation.runOnce();

        assertEquals(List.of(4L, 5L), recipientsOfLast());
    }

    @Test
    void deactivatedAdministratorsAndManagersAreNotAlerted() {
        admin2.deactivate();
        Incident incident = assignedP1();
        afterDeadline(5, 0);
        incident.incrementEscalationLevel();                  // already at level 1, so the next is level 2

        escalation.runOnce();
        assertEquals(List.of(4L), recipientsOfLast());

        manager.deactivate();
        afterDeadline(10, 0);
        escalation.runOnce();

        assertEquals(3, incident.getEscalationLevel());
        assertTrue(recipientsOfLast().isEmpty());             // nobody left to alert, but the level is still recorded
    }

    // ---------- time scale ----------

    @Test
    void theTimeScaleShortensTheTimeoutBetweenLevels() {
        timeScale = 10.0;                                     // a 5 minute timeout lasts 30 seconds
        buildService();
        Incident incident = assignedP1();

        afterDeadline(0, 0);
        escalation.runOnce();
        afterDeadline(0, 29);
        assertEquals(0, escalation.runOnce());
        afterDeadline(0, 30);
        assertEquals(1, escalation.runOnce());

        assertEquals(2, incident.getEscalationLevel());
    }

    // ---------- several incidents and failures ----------

    @Test
    void eachIncidentHasItsOwnLevel() {
        Incident first = assignedP1();
        afterDeadline(0, 0);
        escalation.runOnce();                                 // first is at level 1
        Incident second = assignedP1();                       // same deadline, still at level 0
        published.clear();

        afterDeadline(0, 1);
        assertEquals(1, escalation.runOnce());                // only second is due (level 1); first waits for level 2

        assertEquals(1, first.getEscalationLevel());
        assertEquals(1, second.getEscalationLevel());
        assertEquals(1, published.size());
        assertSame(second, published.get(0).incident());
    }

    @Test
    void aProblemWithOneIncidentDoesNotStopTheOthers() {
        Incident broken = assignedP1();                       // INC100
        Incident healthy = assignedP1();                      // INC101
        numberWhoseSaveFails = broken.getIncidentNumber();
        afterDeadline(0, 0);

        int escalated = escalation.runOnce();

        assertEquals(1, escalated);
        assertEquals(1, published.size());
        assertSame(healthy, published.get(0).incident());     // nothing was announced for the incident that failed
        assertEquals(List.of("INC101 level 1"), saves);
    }

    // ---------- the timer ----------

    @Test
    void theSchedulerCallsTheEscalationEachTimeItRuns() {
        int[] calls = {0};
        EscalationService counting = new EscalationService(null, null, null, null, null, null, clock) {
            @Override
            public int runOnce() {
                calls[0]++;
                return 0;
            }
        };
        EscalationScheduler scheduler = new EscalationScheduler(counting);

        scheduler.run();
        scheduler.run();

        assertEquals(2, calls[0]);
    }

    @Test
    void theSchedulerUsesTheSameIntervalAsTheSlaMonitor() throws Exception {
        Scheduled scheduled = EscalationScheduler.class.getMethod("run").getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("${iitsm.sla.monitor-interval-ms}", scheduled.fixedDelayString());
    }
}
