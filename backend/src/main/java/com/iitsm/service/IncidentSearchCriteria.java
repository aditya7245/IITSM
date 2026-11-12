package com.iitsm.service;

import com.iitsm.domain.incident.IncidentStatus;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.incident.SlaStatus;

import java.time.LocalDate;

/**
 * What to look for when searching incidents. Anything that is null (or, for the number, blank) is not used.
 * Both dates are included in the search.
 *
 * @param incidentNumber part of the incident number, ignoring capital letters
 * @param technicianId   the technician the incident is assigned to
 * @param createdFrom    created on or after this day (UTC)
 * @param createdTo      created on or before this day (UTC)
 */
public record IncidentSearchCriteria(String incidentNumber, IncidentStatus status, Priority priority, Long categoryId,
                                     Long serviceId, Long technicianId, SlaStatus slaStatus, LocalDate createdFrom,
                                     LocalDate createdTo) { }
