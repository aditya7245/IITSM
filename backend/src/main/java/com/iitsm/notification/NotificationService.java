package com.iitsm.notification;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.ITManager;
import com.iitsm.domain.user.User;
import com.iitsm.notification.NotificationRules.Delivery;
import com.iitsm.notification.NotificationRules.Rule;
import com.iitsm.repository.IncidentRepository;
import com.iitsm.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Works out who must be told about an event (see {@link NotificationRules}) and delivers the messages. */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final IncidentRepository incidents;
    private final UserRepository users;
    private final NotificationChannelFactory channels;

    public NotificationService(IncidentRepository incidents, UserRepository users, NotificationChannelFactory channels) {
        this.incidents = incidents;
        this.users = users;
        this.channels = channels;
    }

    /**
     * Looks everything up again from the ids in the request. Called after the original change is committed,
     * so the incident is guaranteed to be visible. A failure to deliver to one channel never stops the others.
     */
    @Transactional
    public void notify(NotificationRequest request) {
        Incident incident = incidents.findById(request.incidentId()).orElse(null);
        if (incident == null) {
            log.warn("No notification sent for {}: incident {} not found", request.type(), request.incidentId());
            return;
        }
        Rule rule = NotificationRules.ruleFor(request.type());

        List<User> admins = rule.needsAdmins() ? allAdmins() : List.of();
        List<User> explicit = request.explicitRecipientIds().isEmpty()
                ? List.of() : users.findAllById(request.explicitRecipientIds());

        List<Delivery> deliveries = NotificationRules.plan(rule, incident.getReporter(), incident.getAssignee(), admins, explicit);
        String text = text(incident, request);
        for (Delivery delivery : deliveries) {
            NotificationMessage message = new NotificationMessage(delivery.user(), incident, request.type(), text);
            deliver(ChannelType.IN_APP, message);
            if (delivery.email()) {
                deliver(ChannelType.EMAIL, message);
            }
        }
    }

    private List<User> allAdmins() {
        List<User> admins = new ArrayList<>(users.findAllByExactType(Administrator.class));
        admins.addAll(users.findAllByExactType(ITManager.class));
        return admins;
    }

    private void deliver(ChannelType type, NotificationMessage message) {
        try {
            channels.get(type).send(message);
        } catch (RuntimeException e) {
            log.warn("Could not deliver {} notification via {}: {}", message.type(), type, e.getMessage());
        }
    }

    private static String text(Incident incident, NotificationRequest request) {
        String text = incident.getIncidentNumber() + ": " + request.type().title();
        if (request.description() != null && !request.description().isBlank()) {
            text += " - " + request.description();
        }
        return text;
    }
}
