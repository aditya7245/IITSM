package com.iitsm.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/** Runs the notification work on a background thread so the user's request is never slowed down by it. */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationService notificationService;

    public NotificationDispatcher(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async
    public void dispatch(NotificationRequest request) {
        try {
            notificationService.notify(request);
        } catch (RuntimeException e) {
            log.warn("Notification for {} failed: {}", request.type(), e.getMessage());
        }
    }
}
