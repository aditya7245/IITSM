// com/iitsm/notification/NotificationChannel.java
public interface NotificationChannel {

    ChannelType type();

    void send(NotificationMessage message);
}
