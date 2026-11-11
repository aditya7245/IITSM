package com.iitsm.dto;

import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;

import java.time.Instant;

/**
 * Everything about one incident. Times are ISO-8601 strings in JSON. Fields that do not apply yet are null:
 * {@code assignee}, {@code priority}, {@code priorityScore}, the deadlines before classification, {@code resolution},
 * {@code duplicateOfNumber} (the number of the incident this one duplicates), {@code slaPausedAt}
 * (set only while the incident waits for the reporter), and the response, resolution and closing times.
 */
public record IncidentDetailResponse(Long id, String incidentNumber, String title, String description,
                                     NamedReference reporter, NamedReference assignee, NamedReference service,
                                     NamedReference category, short impact, short urgency, int affectedUsers,
                                     Integer priorityScore, Priority priority, IncidentStatus status,
                                     SlaStatus slaStatus, Instant responseDeadline, Instant resolutionDeadline,
                                     Instant slaPausedAt, int escalationLevel, String resolution,
                                     String duplicateOfNumber, Instant respondedAt, Instant resolvedAt,
                                     Instant closedAt, Instant createdAt, Instant updatedAt) { }
