package com.iitsm.service;

import com.iitsm.domain.incident.Incident;

import java.util.List;

/**
 * What reporting an incident gives back.
 *
 * @param incident           the saved incident, already prioritised and (if a technician was found) assigned
 * @param possibleDuplicates open incidents that look like this one; empty if none. Only a warning:
 *                           the new incident is created either way
 */
public record CreateIncidentResult(Incident incident, List<DuplicateCandidate> possibleDuplicates) { }
