package com.iitsm.dto;

import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;

import java.time.Instant;

/** One incident as shown in a list. Times are ISO-8601 strings in JSON. {@code assignee} and {@code priority} can be null. */
public record IncidentSummaryResponse(Long id, String incidentNumber, String title, Priority priority,
                                      IncidentStatus status, SlaStatus slaStatus, NamedReference reporter,
                                      NamedReference assignee, NamedReference service, NamedReference category,
                                      Instant resolutionDeadline, Instant createdAt) { }
