package com.iitsm.dto;

import java.time.Instant;

/**
 * One line of an incident's history. {@code user} is null when the system did it (for example an SLA warning);
 * {@code previousState} is null for the first line (creation). The incident fields can be null for lines
 * that do not belong to an incident.
 */
public record AuditResponse(Long id, Long incidentId, String incidentNumber, String action, String previousState,
                            String newState, String description, NamedReference user, Instant createdAt) { }
