package com.iitsm.service;

import com.iitsm.config.SlaProperties;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;
import com.iitsm.domain.sla.SlaEvaluator;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.Technician;
import com.iitsm.observer.IncidentEvent;
import com.iitsm.observer.IncidentEventPublisher;
import com.iitsm.observer.IncidentEventType;
import com.iitsm.repository.IncidentRepository;
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
 * Plain unit tests of the SLA monitor. Every incident is a P1 created at T0: respond within 5 minutes,
 * resolve within 30, warning at 80% of the allowed time (4 and 24 minutes). The repository is a Proxy,
 * the transaction is a hand-written fake that just runs the work, and the clock is moved by the test.
 */
class SlaMonitorServiceTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    static class TestClock extends Clock {
        Instant now = T0;

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    /** Runs the work directly; the real one wraps it in a database transaction. */
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
    SlaMonitorService monitor;
    Employee reporter;
    Technician tech;
    long nextId = 100;

    @BeforeEach
    void setUp() {
        reporter = new Employee("emp1@iitsm.local", "h", "Emma Employee", "Finance");
        tech = new Technician("tech1@iitsm.local", "h", "Tom Technician", null, 5, 4);
        IncidentRepository repository = (IncidentRepository) Proxy.newProxyInstance(
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
                        saves.add(incident.getIncidentNumber() + " " + incident.getSlaStatus());
                        yield incident;
                    }
                    default -> throw new UnsupportedOperationException("Not expected: " + method.getName());
                });
        monitor = new SlaMonitorService(repository, new SlaEvaluator(), new SlaProperties(1.0, 0.8, 30000),
                new IncidentEventPublisher(List.of(published::add)), new DirectTransactions(), clock);
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

    /** ASSIGNED and not yet acknowledged: the response phase is running. */
    private Incident assigned() {
        Incident incident = classified();
        incident.assignTo(tech);
        return incident;
    }

    private Incident classified() {
        ITService payment = new ITService("Payment Application", "Payments", (short) 5);
        Incident incident = new Incident("INC" + nextId, "Payment outage", "Cannot pay", reporter, payment, null,
                (short) 3, (short) 3, 10, T0);
        incident.classify(Priority.P1, 45, T0.plus(Duration.ofMinutes(5)), T0.plus(Duration.ofMinutes(30)));
        setId(incident, nextId);
        stored.put(nextId++, incident);
        return incident;
    }

    /** Acknowledged at the given minute and being worked on: only the resolution phase is running. */
    private Incident inProgress(long acknowledgedAtMinute) {
        Incident incident = assigned();
        incident.acknowledge(T0.plus(Duration.ofMinutes(acknowledgedAtMinute)));
        incident.startProgress();
        return incident;
    }

    private void atMinute(long minutes, long seconds) {
        clock.now = T0.plus(Duration.ofMinutes(minutes)).plusSeconds(seconds);
    }

    private List<IncidentEventType> eventTypes() {
        return published.stream().map(IncidentEvent::type).toList();
    }

    // ---------- the three stages ----------

    @Test
    void anIncidentWithinItsTimeIsLeftAlone() {
        Incident incident = assigned();
        atMinute(3, 59);

        assertEquals(0, monitor.runOnce());

        assertEquals(SlaStatus.ON_TRACK, incident.getSlaStatus());
        assertTrue(published.isEmpty());
        assertTrue(saves.isEmpty());
    }

    @Test
    void atEightyPercentItBecomesAtRiskWithOneWarningAndRepeatRunsAddNothing() {
        Incident incident = assigned();                  // response deadline in 5 minutes: 80% is 4 minutes
        atMinute(4, 0);

        assertEquals(1, monitor.runOnce());
        assertEquals(0, monitor.runOnce());
        atMinute(4, 30);
        assertEquals(0, monitor.runOnce());

        assertEquals(SlaStatus.AT_RISK, incident.getSlaStatus());
        assertEquals(List.of(IncidentEventType.SLA_WARNING), eventTypes());
        assertEquals(List.of("INC100 AT_RISK"), saves);
    }

    @Test
    void afterTheDeadlineItBecomesViolatedWithOneEventAndRepeatRunsAddNothing() {
        Incident incident = assigned();
        atMinute(4, 0);
        monitor.runOnce();                               // warning first
        atMinute(5, 1);                                  // one second past the response deadline

        assertEquals(1, monitor.runOnce());
        assertEquals(0, monitor.runOnce());
        atMinute(20, 0);
        assertEquals(0, monitor.runOnce());

        assertEquals(SlaStatus.VIOLATED, incident.getSlaStatus());
        assertEquals(List.of(IncidentEventType.SLA_WARNING, IncidentEventType.SLA_VIOLATED), eventTypes());
    }

    @Test
    void theDeadlineItselfIsNotYetAViolation() {
        Incident incident = assigned();
        atMinute(5, 0);

        monitor.runOnce();

        assertEquals(SlaStatus.AT_RISK, incident.getSlaStatus());
    }

    @Test
    void ifTheMonitorWasLateItJumpsStraightToViolatedWithoutAWarning() {
        Incident incident = assigned();
        atMinute(10, 0);

        monitor.runOnce();

        assertEquals(SlaStatus.VIOLATED, incident.getSlaStatus());
        assertEquals(List.of(IncidentEventType.SLA_VIOLATED), eventTypes());
    }

    @Test
    void theResolutionPhaseIsWatchedOnceTheIncidentIsAcknowledged() {
        Incident incident = inProgress(1);               // answered in time; resolution due at 30 minutes, warning at 24
        atMinute(23, 59);
        assertEquals(0, monitor.runOnce());
        atMinute(24, 0);
        assertEquals(1, monitor.runOnce());
        atMinute(30, 1);
        assertEquals(1, monitor.runOnce());

        assertEquals(SlaStatus.VIOLATED, incident.getSlaStatus());
        assertEquals(List.of(IncidentEventType.SLA_WARNING, IncidentEventType.SLA_VIOLATED), eventTypes());
    }

    @Test
    void anAnsweredIncidentIsNotFlaggedForTheResponseDeadlineItMet() {
        Incident incident = inProgress(2);               // acknowledged after 2 of the 5 minutes
        atMinute(10, 0);

        assertEquals(0, monitor.runOnce());

        assertEquals(SlaStatus.ON_TRACK, incident.getSlaStatus());
        assertTrue(published.isEmpty());
    }

    // ---------- the clock stops while waiting for the reporter ----------

    @Test
    void timeSpentWaitingForTheReporterDoesNotCount() {
        Incident incident = inProgress(1);
        incident.requestInfo(T0.plus(Duration.ofMinutes(2)));   // paused from minute 2
        atMinute(120, 0);                                        // two hours later

        assertEquals(0, monitor.runOnce());

        assertEquals(IncidentStatus.PENDING_INFO, incident.getStatus());
        assertEquals(SlaStatus.ON_TRACK, incident.getSlaStatus());
        assertTrue(published.isEmpty());
    }

    // ---------- what is announced ----------

    @Test
    void eventsAreDoneByTheSystemAndKeepTheIncidentsStatus() {
        Incident incident = assigned();
        atMinute(4, 0);
        monitor.runOnce();
        atMinute(6, 0);
        monitor.runOnce();

        IncidentEvent warning = published.get(0);
        IncidentEvent violation = published.get(1);
        assertEquals(IncidentEventType.SLA_WARNING, warning.type());
        assertNull(warning.actor());
        assertEquals(IncidentStatus.ASSIGNED, warning.previous());
        assertEquals(IncidentStatus.ASSIGNED, warning.current());
        assertEquals(T0.plus(Duration.ofMinutes(4)), warning.at());
        assertTrue(warning.description().contains("80%"), warning.description());
        assertEquals(IncidentEventType.SLA_VIOLATED, violation.type());
        assertEquals(T0.plus(Duration.ofMinutes(6)), violation.at());
        assertSame(incident, violation.incident());
    }

    @Test
    void severalIncidentsAreEachHandled() {
        Incident late = assigned();
        Incident fine = inProgress(1);
        Incident warned = assigned();
        atMinute(4, 0);                                  // late and warned both reach 80% of 5 minutes; fine does not

        assertEquals(2, monitor.runOnce());

        assertEquals(SlaStatus.AT_RISK, late.getSlaStatus());
        assertEquals(SlaStatus.ON_TRACK, fine.getSlaStatus());
        assertEquals(SlaStatus.AT_RISK, warned.getSlaStatus());
        assertEquals(2, published.size());
    }

    // ---------- which incidents are looked at ----------

    @Test
    void incidentsWithoutAPriorityAreSkipped() {
        ITService payment = new ITService("Payment Application", "Payments", (short) 5);
        Incident unclassified = new Incident("INC1", "t", "d", reporter, payment, null, (short) 3, (short) 3, 1, T0);
        setId(unclassified, 1L);
        stored.put(1L, unclassified);
        atMinute(60, 0);

        assertEquals(0, monitor.runOnce());

        assertTrue(published.isEmpty());
        assertEquals(SlaStatus.ON_TRACK, unclassified.getSlaStatus());
    }

    @Test
    void finishedIncidentsAreNotChecked() {
        Incident cancelled = assigned();
        cancelled.cancel();
        atMinute(60, 0);

        assertEquals(0, monitor.runOnce());

        assertEquals(SlaStatus.ON_TRACK, cancelled.getSlaStatus());
        assertTrue(published.isEmpty());
    }

    // ---------- one failure does not stop the run ----------

    @Test
    void aProblemWithOneIncidentDoesNotStopTheOthers() {
        Incident broken = assigned();                    // INC100
        Incident healthy = assigned();                   // INC101
        numberWhoseSaveFails = broken.getIncidentNumber();
        atMinute(10, 0);

        int raised = monitor.runOnce();

        assertEquals(1, raised);
        assertEquals(1, published.size());
        assertSame(healthy, published.get(0).incident());   // nothing was announced for the incident that failed to save
        assertEquals(List.of("INC101 VIOLATED"), saves);
    }

    // ---------- the timer ----------

    @Test
    void theSchedulerCallsTheMonitorEachTimeItRuns() throws Exception {
        int[] calls = {0};
        SlaMonitorService counting = new SlaMonitorService(null, null, null, null, null, clock) {
            @Override
            public int runOnce() {
                calls[0]++;
                return 0;
            }
        };
        SlaMonitorScheduler scheduler = new SlaMonitorScheduler(counting);

        scheduler.run();
        scheduler.run();

        assertEquals(2, calls[0]);
    }

    @Test
    void theSchedulerIsTimedBySettingInTheConfigurationFile() throws Exception {
        Scheduled scheduled = SlaMonitorScheduler.class.getMethod("run").getAnnotation(Scheduled.class);

        assertNotNull(scheduled);
        assertEquals("${iitsm.sla.monitor-interval-ms}", scheduled.fixedDelayString());
    }
}
