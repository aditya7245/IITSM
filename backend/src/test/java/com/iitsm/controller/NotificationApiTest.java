package com.iitsm.controller;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.notification.Notification;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.NotificationRepository;
import com.iitsm.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The notification endpoints through the real security chain. Notifications are normally written in the background
 * after a commit, which never happens in a test that rolls back, so the test saves them directly. emp1 has three
 * ("first" already read, then "second" and "third" unread), emp2 has one ("other").
 */
class NotificationApiTest extends AbstractApiTest {

    @Autowired NotificationRepository notifications;
    @Autowired IncidentRepository incidents;

    Notification first;
    Notification second;
    Notification third;
    Notification other;

    @BeforeEach
    void saveNotifications() throws Exception {
        long incidentId = submitIncident("emp1", "Payment application outage", "Users cannot pay",
                "Application", "Payment Application", 3, 3, 250);
        Incident incident = incidents.findById(incidentId).orElseThrow();
        Notification read = new Notification(user("emp1"), incident, "ASSIGNED", "first");
        read.markRead();
        first = notifications.save(read);
        second = notifications.save(new Notification(user("emp1"), incident, "ACKNOWLEDGED", "second"));
        third = notifications.save(new Notification(user("emp1"), incident, "RESOLVED", "third"));
        other = notifications.save(new Notification(user("emp2"), incident, "CREATED", "other"));
    }

    private List<String> messages(String user, String query) throws Exception {
        return list(body(getAs(user, "/api/notifications" + query).andExpect(status().isOk())), "$.content[*].message");
    }

    private long unread(String user) throws Exception {
        return number(body(getAs(user, "/api/notifications/unread-count").andExpect(status().isOk())), "$.count");
    }

    private String read(long id, String user) throws Exception {
        return body(postAs(user, "/api/notifications/" + id + "/read").andExpect(status().isOk()));
    }

    // ---------- reading ----------

    @Test
    void aUserSeesTheirOwnNotificationsNewestFirst() throws Exception {
        assertEquals(List.of("third", "second", "first"), messages("emp1", ""));
    }

    @Test
    void eachNotificationCarriesItsDetails() throws Exception {
        String body = body(getAs("emp1", "/api/notifications?size=1").andExpect(status().isOk()));

        assertEquals(third.getId(), number(body, "$.content[0].id"));
        assertEquals("RESOLVED", text(body, "$.content[0].eventType"));
        assertEquals("third", text(body, "$.content[0].message"));
        assertEquals(false, JsonPath.<Boolean>read(body, "$.content[0].read"));
        assertTrue(text(body, "$.content[0].incidentNumber").matches("INC\\d+"));
        assertNotNull(text(body, "$.content[0].createdAt"));
    }

    @Test
    void unreadOnlyLeavesOutTheOnesAlreadyRead() throws Exception {
        assertEquals(List.of("third", "second"), messages("emp1", "?unreadOnly=true"));
        assertEquals(List.of("third", "second", "first"), messages("emp1", "?unreadOnly=false"));
    }

    @Test
    void thePagesHaveTheRightContentAndTotals() throws Exception {
        String page0 = body(getAs("emp1", "/api/notifications?size=2&page=0").andExpect(status().isOk()));
        assertEquals(List.of("third", "second"), list(page0, "$.content[*].message"));
        assertEquals(3, number(page0, "$.totalElements"));
        assertEquals(2, number(page0, "$.totalPages"));

        String page1 = body(getAs("emp1", "/api/notifications?size=2&page=1").andExpect(status().isOk()));
        assertEquals(List.of("first"), list(page1, "$.content[*].message"));

        String unreadPage = body(getAs("emp1", "/api/notifications?unreadOnly=true&size=1&page=1").andExpect(status().isOk()));
        assertEquals(List.of("second"), list(unreadPage, "$.content[*].message"));
        assertEquals(2, number(unreadPage, "$.totalElements"));
    }

    @Test
    void aSortInTheRequestDoesNotChangeTheOrderOrFail() throws Exception {
        assertEquals(List.of("third", "second", "first"), messages("emp1", "?sort=message,asc"));
        assertEquals(List.of("third", "second", "first"), messages("emp1", "?sort=nonsense"));
    }

    @Test
    void theLargestPageIsOneHundred() throws Exception {
        assertEquals(100, number(body(getAs("emp1", "/api/notifications?size=500").andExpect(status().isOk())), "$.size"));
        assertEquals(20, number(body(getAs("emp1", "/api/notifications").andExpect(status().isOk())), "$.size"));
    }

    @Test
    void aUserNeverSeesAnotherUsersNotifications() throws Exception {
        assertEquals(List.of("other"), messages("emp2", ""));
        assertEquals(List.of("other"), messages("emp2", "?unreadOnly=true"));
        assertEquals(List.of(), messages("tech1", ""));
        assertEquals(List.of(), messages("admin", ""));
        assertEquals(1, number(body(getAs("emp2", "/api/notifications").andExpect(status().isOk())), "$.totalElements"));
    }

    // ---------- the unread count ----------

    @Test
    void theUnreadCountIsPerUser() throws Exception {
        assertEquals(2, unread("emp1"));
        assertEquals(1, unread("emp2"));
        assertEquals(0, unread("admin"));
    }

    // ---------- marking as read ----------

    @Test
    void anOwnerMarksOneAsReadAndTheCountGoesDown() throws Exception {
        String body = read(third.getId(), "emp1");

        assertEquals(true, JsonPath.<Boolean>read(body, "$.read"));
        assertEquals("third", text(body, "$.message"));
        assertEquals(1, unread("emp1"));
        assertEquals(List.of("second"), messages("emp1", "?unreadOnly=true"));
    }

    @Test
    void markingOneThatIsAlreadyReadChangesNothing() throws Exception {
        read(second.getId(), "emp1");
        String again = read(second.getId(), "emp1");
        String alreadyRead = read(first.getId(), "emp1");

        assertEquals(true, JsonPath.<Boolean>read(again, "$.read"));
        assertEquals(true, JsonPath.<Boolean>read(alreadyRead, "$.read"));
        assertEquals(1, unread("emp1"));
    }

    @Test
    void aUserCannotMarkSomeoneElsesNotificationAsRead() throws Exception {
        postAs("emp2", "/api/notifications/" + second.getId() + "/read").andExpect(status().isForbidden());
        postAs("emp1", "/api/notifications/" + other.getId() + "/read").andExpect(status().isForbidden());
        postAs("admin", "/api/notifications/" + second.getId() + "/read").andExpect(status().isForbidden());

        assertEquals(2, unread("emp1"));       // nothing changed
        assertEquals(1, unread("emp2"));
    }

    @Test
    void anUnknownNotificationIs404AndABadIdIs400() throws Exception {
        postAs("emp1", "/api/notifications/999999/read").andExpect(status().isNotFound());
        postAs("emp1", "/api/notifications/abc/read").andExpect(status().isBadRequest());
    }

    @Test
    void readAllMarksOnlyTheCallersOwnNotifications() throws Exception {
        String marked = body(postAs("emp1", "/api/notifications/read-all").andExpect(status().isOk()));

        assertEquals(2, number(marked, "$.marked"));
        assertEquals(0, unread("emp1"));
        assertEquals(1, unread("emp2"));                                           // untouched
        assertEquals(0, number(body(postAs("emp1", "/api/notifications/read-all").andExpect(status().isOk())), "$.marked"));
        assertEquals(List.of(), messages("emp1", "?unreadOnly=true"));
        assertEquals(3, number(body(getAs("emp1", "/api/notifications").andExpect(status().isOk())), "$.totalElements"));
    }

    // ---------- who is logged in ----------

    @Test
    void everyAddressNeedsALogin() throws Exception {
        getAs(null, "/api/notifications").andExpect(status().isUnauthorized());
        getAs(null, "/api/notifications/unread-count").andExpect(status().isUnauthorized());
        postAs(null, "/api/notifications/" + second.getId() + "/read").andExpect(status().isUnauthorized());
        postAs(null, "/api/notifications/read-all").andExpect(status().isUnauthorized());
    }
}
