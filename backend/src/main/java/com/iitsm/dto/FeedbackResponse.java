package com.iitsm.dto;

import java.time.Instant;

/** The reporter's rating (1 to 5) and optional comment on how an incident was handled. */
public record FeedbackResponse(Long id, Long incidentId, int rating, String comments, Instant createdAt) { }
