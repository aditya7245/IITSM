package com.iitsm.observer;

import com.iitsm.notification.NotificationDispatcher;
import com.iitsm.notification.NotificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Turns incident events into notifications, but only once the change that caused them is safely committed:
 * people are never told about something that was then rolled back, and the sending happens in the background.
 * This observer never throws, so a notification problem can never break an incident update.
 */
@Component
@Order(2)
public class NotificationObserver implements IncidentObserver {

    private static final Logger log = LoggerFactory.getLogger(NotificationObserver.class);

    private final NotificationDispatcher dispatcher;

    public NotificationObserver(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void onEvent(IncidentEvent event) {
        try {
            Long incidentId = event.incident().getId();
            if (incidentId == null) {
                log.warn("No notification for {}: the incident has not been saved yet", event.type());
                return;
            }
            NotificationRequest request = new NotificationRequest(
                    event.type(), incidentId, event.description(), event.explicitRecipientIds());

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatchSafely(request);
                    }
                });
            } else {
                dispatchSafely(request);
            }
        } catch (RuntimeException e) {
            log.warn("Could not schedule notification for {}: {}", event.type(), e.getMessage());
        }
    }

    private void dispatchSafely(NotificationRequest request) {
        try {
            dispatcher.dispatch(request);
        } catch (RuntimeException e) {
            log.warn("Could not dispatch notification for {}: {}", request.type(), e.getMessage());
        }
    }
}
