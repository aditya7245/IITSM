package com.iitsm.dto;

import com.iitsm.domain.incident.Priority;

/** Minutes allowed to respond and to resolve. {@code service} is null for the default policy of a priority. */
public record SlaPolicyResponse(Long id, Priority priority, NamedReference service, int responseMinutes,
                                int resolutionMinutes) { }
