package com.iitsm.dto;

/** Whether team leads take part in automatic assignment like technicians (otherwise only by hand or when an incident is escalated). */
public record TeamLeadAssignmentResponse(boolean enabled) { }
