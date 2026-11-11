package com.iitsm.dto;

import com.iitsm.domain.incident.CommentType;

import java.time.Instant;

/** One entry in the conversation on an incident. */
public record CommentResponse(Long id, CommentType type, String body, NamedReference author, Instant createdAt) { }
