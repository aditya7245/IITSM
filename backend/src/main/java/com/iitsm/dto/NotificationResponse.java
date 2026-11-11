package com.iitsm.dto;

import java.time.Instant;

/** One in-app notification. {@code eventType} is the name of the event, such as ASSIGNED or SLA_WARNING. */
public record NotificationResponse(Long id, Long incidentId, String incidentNumber, String eventType, String message,
                                   boolean read, Instant createdAt) { }
