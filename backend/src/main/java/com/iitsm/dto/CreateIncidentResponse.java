package com.iitsm.dto;

import com.iitsm.service.DuplicateCandidate;

import java.util.List;

/** The new incident, plus any open incidents that look like it (only a warning; the incident was created anyway). */
public record CreateIncidentResponse(IncidentDetailResponse incident, List<DuplicateCandidate> possibleDuplicates) { }
