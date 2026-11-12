package com.iitsm.dto;

/** How busy a technician is: {@code active} incidents counting toward their limit, out of at most {@code max}. */
public record TechnicianWorkloadResponse(int active, int max) { }
