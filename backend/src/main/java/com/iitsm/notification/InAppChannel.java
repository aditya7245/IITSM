package com.iitsm.notification;

import com.iitsm.domain.notification.Notification;
import com.iitsm.repository.NotificationRepository;
import org.springframework.stereotype.Component;

/** Stores the message so the user sees it in the app. */
@Component
public class InAppChannel implements NotificationChannel {

    /** Length of the message column in the database. */
    static final int MAX_LENGTH = 500;

    private final NotificationRepository notifications;

    public InAppChannel(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    @Override
    public ChannelType type() {
        return ChannelType.IN_APP;
    }

    @Override
    public void send(NotificationMessage message) {
        String text = message.text();
        if (text.length() > MAX_LENGTH) {
            text = text.substring(0, MAX_LENGTH - 3) + "...";
        }
        notifications.save(new Notification(message.recipient(), message.incident(), message.type().name(), text));
    }
}
