package com.iitsm.notification;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Factory pattern: hands out the channel for a {@link ChannelType}. Spring supplies every channel automatically. */
@Component
public class NotificationChannelFactory {

    private final Map<ChannelType, NotificationChannel> channels = new EnumMap<>(ChannelType.class);

    public NotificationChannelFactory(List<NotificationChannel> available) {
        for (NotificationChannel channel : available) {
            if (channels.put(channel.type(), channel) != null) {
                throw new IllegalStateException("Two notification channels claim the type " + channel.type());
            }
        }
    }

    /** @throws IllegalArgumentException if no channel exists for the type */
    public NotificationChannel get(ChannelType type) {
        NotificationChannel channel = type == null ? null : channels.get(type);
        if (channel == null) {
            throw new IllegalArgumentException("No notification channel for " + type);
        }
        return channel;
    }
}
