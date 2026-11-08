package com.iitsm.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NotificationChannelFactoryTest {

    private static NotificationChannel fake(ChannelType type) {
        return new NotificationChannel() {
            @Override public ChannelType type() { return type; }
            @Override public void send(NotificationMessage message) { }
        };
    }

    @Test
    void returnsTheChannelForEachType() {
        NotificationChannel inApp = fake(ChannelType.IN_APP);
        NotificationChannel email = fake(ChannelType.EMAIL);
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of(email, inApp));

        assertSame(inApp, factory.get(ChannelType.IN_APP));
        assertSame(email, factory.get(ChannelType.EMAIL));
    }

    @Test
    void aMissingChannelIsReportedClearly() {
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of(fake(ChannelType.IN_APP)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> factory.get(ChannelType.EMAIL));
        assertTrue(e.getMessage().contains("EMAIL"));
        assertThrows(IllegalArgumentException.class, () -> factory.get(null));
    }

    @Test
    void twoChannelsWithTheSameTypeAreRejected() {
        assertThrows(IllegalStateException.class,
                () -> new NotificationChannelFactory(List.of(fake(ChannelType.EMAIL), fake(ChannelType.EMAIL))));
    }

    @Test
    void anEmptyFactoryRejectsEveryRequest() {
        NotificationChannelFactory factory = new NotificationChannelFactory(List.of());
        assertThrows(IllegalArgumentException.class, () -> factory.get(ChannelType.IN_APP));
    }
}
