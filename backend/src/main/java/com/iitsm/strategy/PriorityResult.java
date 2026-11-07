package com.iitsm.strategy;

import com.iitsm.domain.incident.Priority;

/** The outcome of a priority calculation: the raw score and the priority tier it maps to. */
public record PriorityResult(int score, Priority priority) { }
