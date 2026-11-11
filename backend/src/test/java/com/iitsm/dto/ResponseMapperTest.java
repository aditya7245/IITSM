package com.iitsm.dto;

import com.iitsm.domain.audit.AuditLog;
import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.incident.CommentType;
import com.iitsm.domain.incident.Feedback;
import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.incident.IncidentComment;
import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;
import com.iitsm.domain.notification.Notification;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.Technician;
import com.iitsm.service.CategoryScore;
import com.iitsm.service.CreateIncidentResult;
import com.iitsm.service.DuplicateCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests of {@link ResponseMapper}: every response object gets the right values, and missing things become null. */
class ResponseMapperTest {

    private static final Instant T0 = Instant.parse("2026-11-10T09:00:00Z");

    ResponseMapper mapper;
    Employee reporter;
    Technician tech;
    ITService payment;
    Category application;

    @BeforeEach
    void setUp() {
        mapper = new ResponseMapper();
        reporter = set(new Employee("emp1@iitsm.local", "h", "Emma Employee", "Finance"), "id", 1L);
        Team team = new Team("Application Support");
        tech = set(new Technician("tech1@iitsm.local", "h", "Tom Technician", team, 5, 4), "id", 3L);
        payment = set(new ITService("Payment Application", "Payments", (short) 5), "id", 7L);
        application = set(new Category("Application", "Business applications", "payment,billing", null), "id", 11L);
    }

    @SuppressWarnings("unchecked")
    private static <T> T set(T target, String fieldName, Object value) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return target;
            } catch (NoSuchFieldException e) {
                // look in the parent class
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("No field " + fieldName + " on " + target.getClass());
    }

    private Incident newIncident(Long id) {
        Incident incident = new Incident("INC1001", "Payment outage", "Users cannot pay", reporter, payment, application,
                (short) 3, (short) 3, 250, T0);
        set(incident, "id", id);
        set(incident, "updatedAt", T0.plusSeconds(1));
        return incident;
    }

    private Incident workedIncident() {
        Incident incident = newIncident(100L);
        incident.classify(Priority.P1, 45, T0.plus(Duration.ofMinutes(5)), T0.plus(Duration.ofMinutes(30)));
        incident.assignTo(tech);
        incident.acknowledge(T0.plusSeconds(90));
        return incident;
    }

    // ---------- incidents ----------

    @Test
    void detailCarriesEveryFieldOfAWorkedIncident() {
        Incident incident = workedIncident();

        IncidentDetailResponse detail = mapper.toDetail(incident);

        assertEquals(100L, detail.id());
        assertEquals("INC1001", detail.incidentNumber());
        assertEquals("Payment outage", detail.title());
        assertEquals("Users cannot pay", detail.description());
        assertEquals(new NamedReference(1L, "Emma Employee"), detail.reporter());
        assertEquals(new NamedReference(3L, "Tom Technician"), detail.assignee());
        assertEquals(new NamedReference(7L, "Payment Application"), detail.service());
        assertEquals(new NamedReference(11L, "Application"), detail.category());
        assertEquals(3, detail.impact());
        assertEquals(3, detail.urgency());
        assertEquals(250, detail.affectedUsers());
        assertEquals(45, detail.priorityScore().intValue());
        assertEquals(Priority.P1, detail.priority());
        assertEquals(IncidentStatus.ACKNOWLEDGED, detail.status());
        assertEquals(SlaStatus.ON_TRACK, detail.slaStatus());
        assertEquals(T0.plus(Duration.ofMinutes(5)), detail.responseDeadline());
        assertEquals(T0.plus(Duration.ofMinutes(30)), detail.resolutionDeadline());
        assertNull(detail.slaPausedAt());
        assertEquals(0, detail.escalationLevel());
        assertNull(detail.resolution());
        assertNull(detail.duplicateOfNumber());
        assertEquals(T0.plusSeconds(90), detail.respondedAt());
        assertNull(detail.resolvedAt());
        assertNull(detail.closedAt());
        assertEquals(T0, detail.createdAt());
        assertEquals(T0.plusSeconds(1), detail.updatedAt());
    }

    @Test
    void anIncidentThatIsNotClassifiedYetHasNullsInsteadOfFailing() {
        Incident incident = new Incident("INC1002", "Printer", "Jammed", reporter, null, null,
                (short) 1, (short) 1, 1, T0);

        IncidentDetailResponse detail = mapper.toDetail(incident);

        assertNull(detail.assignee());
        assertNull(detail.service());
        assertNull(detail.category());
        assertNull(detail.priority());
        assertNull(detail.priorityScore());
        assertNull(detail.responseDeadline());
        assertNull(detail.resolutionDeadline());
        assertEquals(IncidentStatus.NEW, detail.status());
        assertEquals(new NamedReference(1L, "Emma Employee"), detail.reporter());
    }

    @Test
    void aDuplicateNamesTheIncidentItDuplicates() {
        Incident original = workedIncident();
        Incident copy = new Incident("INC1002", "Payment is down", "Cannot pay", reporter, payment, application,
                (short) 3, (short) 3, 10, T0);
        copy.markDuplicateOf(original);

        assertEquals("INC1001", mapper.toDetail(copy).duplicateOfNumber());
    }

    @Test
    void aResolvedIncidentShowsItsResolutionAndTimes() {
        Incident incident = workedIncident();
        incident.startProgress();
        incident.resolve("Restarted the gateway", T0.plusSeconds(600));

        IncidentDetailResponse detail = mapper.toDetail(incident);

        assertEquals("Restarted the gateway", detail.resolution());
        assertEquals(T0.plusSeconds(600), detail.resolvedAt());
        assertEquals(IncidentStatus.RESOLVED, detail.status());
    }

    @Test
    void aWaitingIncidentShowsWhenItsClockWasPaused() {
        Incident incident = workedIncident();
        incident.startProgress();
        incident.requestInfo(T0.plusSeconds(300));

        assertEquals(T0.plusSeconds(300), mapper.toDetail(incident).slaPausedAt());
    }

    @Test
    void summaryCarriesTheListFields() {
        IncidentSummaryResponse summary = mapper.toSummary(workedIncident());

        assertEquals(100L, summary.id());
        assertEquals("INC1001", summary.incidentNumber());
        assertEquals("Payment outage", summary.title());
        assertEquals(Priority.P1, summary.priority());
        assertEquals(IncidentStatus.ACKNOWLEDGED, summary.status());
        assertEquals(SlaStatus.ON_TRACK, summary.slaStatus());
        assertEquals(new NamedReference(1L, "Emma Employee"), summary.reporter());
        assertEquals(new NamedReference(3L, "Tom Technician"), summary.assignee());
        assertEquals(new NamedReference(7L, "Payment Application"), summary.service());
        assertEquals(new NamedReference(11L, "Application"), summary.category());
        assertEquals(T0.plus(Duration.ofMinutes(30)), summary.resolutionDeadline());
        assertEquals(T0, summary.createdAt());
    }

    @Test
    void createResponseHoldsTheDetailAndThePossibleDuplicates() {
        Incident incident = workedIncident();
        DuplicateCandidate candidate = new DuplicateCandidate(42L, "INC1000", "Payment app down", 0.71);

        CreateIncidentResponse response = mapper.toCreateResponse(new CreateIncidentResult(incident, List.of(candidate)));

        assertEquals("INC1001", response.incident().incidentNumber());
        assertEquals(List.of(candidate), response.possibleDuplicates());
    }

    // ---------- comments, history, notifications, feedback ----------

    @Test
    void commentCarriesTypeBodyAuthorAndTime() {
        IncidentComment comment = set(set(new IncidentComment(workedIncident(), tech, CommentType.INFO_REQUEST,
                "Which users are affected?"), "id", 5L), "createdAt", T0.plusSeconds(10));

        CommentResponse response = mapper.toComment(comment);

        assertEquals(5L, response.id());
        assertEquals(CommentType.INFO_REQUEST, response.type());
        assertEquals("Which users are affected?", response.body());
        assertEquals(new NamedReference(3L, "Tom Technician"), response.author());
        assertEquals(T0.plusSeconds(10), response.createdAt());
    }

    @Test
    void auditLineCarriesTheStatesTheUserAndTheIncident() {
        AuditLog line = set(set(new AuditLog(workedIncident(), tech, "ACKNOWLEDGED", "ASSIGNED", "ACKNOWLEDGED",
                "Acknowledged by Tom Technician", T0.plusSeconds(90)), "id", 9L), "createdAt", T0.plusSeconds(90));

        AuditResponse response = mapper.toAudit(line);

        assertEquals(9L, response.id());
        assertEquals(100L, response.incidentId());
        assertEquals("INC1001", response.incidentNumber());
        assertEquals("ACKNOWLEDGED", response.action());
        assertEquals("ASSIGNED", response.previousState());
        assertEquals("ACKNOWLEDGED", response.newState());
        assertEquals("Acknowledged by Tom Technician", response.description());
        assertEquals(new NamedReference(3L, "Tom Technician"), response.user());
        assertEquals(T0.plusSeconds(90), response.createdAt());
    }

    @Test
    void aLineDoneByTheSystemHasNoUserAndTheFirstLineHasNoPreviousState() {
        AuditLog line = new AuditLog(workedIncident(), null, "CREATED", null, "NEW", "Reported", T0);

        AuditResponse response = mapper.toAudit(line);

        assertNull(response.user());
        assertNull(response.previousState());
        assertEquals("NEW", response.newState());
    }

    @Test
    void notificationCarriesTheMessageTheEventAndWhetherItWasRead() {
        Notification notification = set(set(new Notification(reporter, workedIncident(), "ASSIGNED",
                "INC1001: assigned to Tom Technician"), "id", 21L), "createdAt", T0.plusSeconds(5));

        NotificationResponse response = mapper.toNotification(notification);

        assertEquals(21L, response.id());
        assertEquals(100L, response.incidentId());
        assertEquals("INC1001", response.incidentNumber());
        assertEquals("ASSIGNED", response.eventType());
        assertEquals("INC1001: assigned to Tom Technician", response.message());
        assertFalse(response.read());
        assertEquals(T0.plusSeconds(5), response.createdAt());
    }

    @Test
    void aReadNotificationIsMarkedRead() {
        Notification notification = new Notification(reporter, workedIncident(), "ASSIGNED", "message");
        notification.markRead();

        assertTrue(mapper.toNotification(notification).read());
    }

    @Test
    void feedbackCarriesRatingCommentAndIncident() {
        Feedback feedback = set(set(new Feedback(workedIncident(), (short) 4, "Fast and friendly"), "id", 2L),
                "createdAt", T0.plusSeconds(700));

        FeedbackResponse response = mapper.toFeedback(feedback);

        assertEquals(2L, response.id());
        assertEquals(100L, response.incidentId());
        assertEquals(4, response.rating());
        assertEquals("Fast and friendly", response.comments());
        assertEquals(T0.plusSeconds(700), response.createdAt());
    }

    // ---------- technicians, suggestions, pages ----------

    @Test
    void technicianSummaryCarriesTeamExperienceAndWorkloadLimit() {
        TechnicianSummaryResponse response = mapper.toTechnician(tech);

        assertEquals(3L, response.id());
        assertEquals("Tom Technician", response.fullName());
        assertEquals("Application Support", response.teamName());
        assertEquals(4, response.experienceYears());
        assertEquals(5, response.maxWorkload());
        assertTrue(response.available());
    }

    @Test
    void aTechnicianWithoutATeamHasANullTeamName() {
        Technician loner = set(new Technician("t@iitsm.local", "h", "Lone Tech", null, 3, 1), "id", 8L);

        assertNull(mapper.toTechnician(loner).teamName());
    }

    @Test
    void suggestionCarriesTheCategoryAndItsScore() {
        CategorySuggestionResponse response = mapper.toSuggestion(new CategoryScore(application, 2));

        assertEquals(11L, response.categoryId());
        assertEquals("Application", response.name());
        assertEquals(2, response.score());
    }

    @Test
    void aPageKeepsItsNumbersAndMapsItsContent() {
        PageImpl<Incident> page = new PageImpl<>(List.of(workedIncident()), PageRequest.of(2, 10), 21);

        PageResponse<IncidentSummaryResponse> response = PageResponse.from(page, mapper::toSummary);

        assertEquals(1, response.content().size());
        assertEquals("INC1001", response.content().get(0).incidentNumber());
        assertEquals(2, response.page());
        assertEquals(10, response.size());
        assertEquals(21, response.totalElements());
        assertEquals(3, response.totalPages());
    }

    @Test
    void anEmptyPageHasNoContentAndZeroPages() {
        PageResponse<IncidentSummaryResponse> response = PageResponse.from(
                new PageImpl<Incident>(List.of(), PageRequest.of(0, 20), 0), mapper::toSummary);

        assertTrue(response.content().isEmpty());
        assertEquals(0, response.totalElements());
        assertEquals(0, response.totalPages());
    }
}
