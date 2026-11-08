package com.iitsm.notification;

import com.iitsm.domain.incident.Incident;
import com.iitsm.domain.user.Employee;
import com.iitsm.observer.IncidentEventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailChannelTest {

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final EmailChannel channel = new EmailChannel(mailSender, "noreply@iitsm.local");

    private final Employee recipient = new Employee("emp1@iitsm.local", "hash", "Asha", "Finance");
    private final Incident incident = new Incident("INC1001", "Payment down", "Cannot pay", null, null, null,
            (short) 3, (short) 3, 10, Instant.parse("2026-11-10T09:00:00Z"));

    @Test
    void sendsAPlainTextEmailWithTheIncidentNumberInTheSubject() {
        channel.send(new NotificationMessage(recipient, incident, IncidentEventType.CREATED, "INC1001 was created"));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage mail = sent.getValue();
        assertEquals("noreply@iitsm.local", mail.getFrom());
        assertEquals("emp1@iitsm.local", mail.getTo()[0]);
        assertEquals("[INC1001] Incident created", mail.getSubject());
        assertEquals("INC1001 was created", mail.getText());
    }

    @Test
    void aMailFailureIsSwallowedSoTheCallerIsNeverBroken() {
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertDoesNotThrow(() ->
                channel.send(new NotificationMessage(recipient, incident, IncidentEventType.RESOLVED, "fixed")));
    }

    @Test
    void aRecipientWithoutAnEmailAddressIsSkipped() {
        Employee noEmail = new Employee(" ", "hash", "Nobody", "Finance");
        channel.send(new NotificationMessage(noEmail, incident, IncidentEventType.CREATED, "x"));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void withoutAnIncidentTheSubjectIsJustTheTitle() {
        channel.send(new NotificationMessage(recipient, null, IncidentEventType.SLA_VIOLATED, "x"));

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        assertEquals("SLA violated", sent.getValue().getSubject());
    }

    @Test
    void reportsItsType() {
        assertEquals(ChannelType.EMAIL, channel.type());
    }
}
