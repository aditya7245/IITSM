package com.iitsm.domain.sla;

import java.time.Instant;

/** The two SLA deadlines of an incident. */
public record Deadlines(Instant responseDeadline, Instant resolutionDeadline) { }
