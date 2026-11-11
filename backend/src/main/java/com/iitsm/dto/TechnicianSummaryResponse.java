package com.iitsm.dto;

/** A technician in a list, for example in the assignment recommendations. {@code teamName} can be null. */
public record TechnicianSummaryResponse(Long id, String fullName, String teamName, int experienceYears,
                                        int maxWorkload, boolean available) { }
