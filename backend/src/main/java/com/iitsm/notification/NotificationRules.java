package com.iitsm.notification;

import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.observer.IncidentEventType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.iitsm.notification.NotificationRules.Audience.*;
import static com.iitsm.notification.NotificationRules.EmailMode.*;
import static com.iitsm.observer.IncidentEventType.*;

/**
 * Who is told about which event, and whether they also get an email. Every notification appears in the app;
 * the email mode adds an email on top. This class is pure logic with no database, so the whole table is unit tested.
 */
public final class NotificationRules {

    /** The groups of people a rule can address. */
    public enum Audience { REPORTER, ASSIGNEE, TEAM_LEAD_OF_ASSIGNEE, ALL_ADMINS }

    /** Who gets an email as well as the in-app notification. */
    public enum EmailMode { NONE, ALL, ASSIGNEE_ONLY }

    public record Rule(List<Audience> audiences, EmailMode emailMode) {
        public boolean needsAdmins() {
            return audiences.contains(ALL_ADMINS);
        }
    }

    /** One person to notify, and whether they get an email too. */
    public record Delivery(User user, boolean email) { }

    private static final Map<IncidentEventType, Rule> RULES = new EnumMap<>(IncidentEventType.class);

    static {
        rule(CREATED, ALL, REPORTER);
        rule(CLASSIFIED, NONE);
        rule(PRIORITY_CHANGED, NONE, REPORTER, ASSIGNEE);
        rule(ASSIGNED, ASSIGNEE_ONLY, ASSIGNEE, REPORTER);
        rule(ASSIGNMENT_FAILED, ALL, ALL_ADMINS);
        rule(ACKNOWLEDGED, NONE, REPORTER);
        rule(INVESTIGATION_STARTED, NONE);
        rule(INFO_REQUESTED, ALL, REPORTER);
        rule(INFO_RECEIVED, NONE, ASSIGNEE);
        rule(RESOLVED, ALL, REPORTER);
        rule(REOPENED, NONE, ASSIGNEE);
        rule(VERIFIED, NONE, ASSIGNEE);
        rule(CLOSED, NONE, REPORTER);
        rule(CANCELLED, NONE, REPORTER, ASSIGNEE);
        rule(MARKED_DUPLICATE, NONE, REPORTER, ASSIGNEE);
        rule(SLA_WARNING, NONE, ASSIGNEE, TEAM_LEAD_OF_ASSIGNEE);
        rule(SLA_VIOLATED, ALL, ASSIGNEE, ALL_ADMINS);
        rule(ESCALATED, ALL);   // only the explicit recipients named in the event
    }

    private NotificationRules() { }

    private static void rule(IncidentEventType type, EmailMode emailMode, Audience... audiences) {
        RULES.put(type, new Rule(List.of(audiences), emailMode));
    }

    public static Rule ruleFor(IncidentEventType type) {
        return RULES.get(type);
    }

    /**
     * Works out who to notify for an event.
     *
     * @param assignee           the assigned technician, or null
     * @param admins             all administrators and IT managers (only used if the rule addresses them)
     * @param explicitRecipients if not empty, these people are notified instead of the rule's audiences
     * @return each person once, with an email if any of their reasons for being notified calls for one
     */
    public static List<Delivery> plan(Rule rule, User reporter, Technician assignee, List<User> admins,
                                      List<User> explicitRecipients) {
        Map<User, Boolean> emailByUser = new LinkedHashMap<>();

        if (!explicitRecipients.isEmpty()) {
            for (User user : explicitRecipients) {
                add(emailByUser, user, wantsEmail(rule, user, assignee));
            }
        } else {
            for (Audience audience : rule.audiences()) {
                switch (audience) {
                    case REPORTER -> add(emailByUser, reporter, wantsEmail(rule, reporter, assignee));
                    case ASSIGNEE -> add(emailByUser, assignee, wantsEmail(rule, assignee, assignee));
                    case TEAM_LEAD_OF_ASSIGNEE -> {
                        User lead = teamLeadOf(assignee);
                        add(emailByUser, lead, wantsEmail(rule, lead, assignee));
                    }
                    case ALL_ADMINS -> {
                        for (User admin : admins) {
                            add(emailByUser, admin, wantsEmail(rule, admin, assignee));
                        }
                    }
                }
            }
        }

        List<Delivery> deliveries = new ArrayList<>();
        emailByUser.forEach((user, email) -> deliveries.add(new Delivery(user, email)));
        return deliveries;
    }

    private static void add(Map<User, Boolean> map, User user, boolean email) {
        if (user != null) {
            map.merge(user, email, Boolean::logicalOr);
        }
    }

    private static boolean wantsEmail(Rule rule, User user, Technician assignee) {
        return switch (rule.emailMode()) {
            case ALL -> true;
            case ASSIGNEE_ONLY -> assignee != null && assignee.equals(user);
            case NONE -> false;
        };
    }

    private static User teamLeadOf(Technician assignee) {
        if (assignee == null || assignee.getTeam() == null) {
            return null;
        }
        return assignee.getTeam().getLead();
    }
}
