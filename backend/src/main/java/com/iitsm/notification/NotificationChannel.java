package com.iitsm.notification;

/**
 * One way of delivering a notification (in the app, by email, later perhaps SMS). To add a channel, write a class
 * that implements this interface and add a value to {@link ChannelType}; nothing else needs to change.
 */
public interface NotificationChannel {

    ChannelType type();

    void send(NotificationMessage message);
}
