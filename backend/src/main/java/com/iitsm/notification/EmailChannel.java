package com.iitsm.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends the message as a plain-text email. In development the mail goes to Mailpit (http://localhost:8025),
 * so nothing real is sent. A mail problem is logged and swallowed: failing to send an email must never
 * break the incident update that caused it.
 */
@Component
public class EmailChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailChannel.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailChannel(JavaMailSender mailSender, @Value("${iitsm.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public ChannelType type() {
        return ChannelType.EMAIL;
    }

    @Override
    public void send(NotificationMessage message) {
        String to = message.recipient().getEmail();
        if (to == null || to.isBlank()) {
            log.warn("Not sending email for {}: recipient has no email address", message.type());
            return;
        }
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(to);
            mail.setSubject(subject(message));
            mail.setText(message.text());
            mailSender.send(mail);
        } catch (RuntimeException e) {
            log.warn("Could not send {} email to {}: {}", message.type(), to, e.getMessage());
        }
    }

    private static String subject(NotificationMessage message) {
        String title = message.type().title();
        if (message.incident() == null) {
            return title;
        }
        return "[" + message.incident().getIncidentNumber() + "] " + title;
    }
}
