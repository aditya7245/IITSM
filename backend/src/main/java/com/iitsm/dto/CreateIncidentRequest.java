package com.iitsm.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What an employee fills in when reporting a problem. The limits are the same ones the service layer enforces. */
public record CreateIncidentRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 5000) String description,
        @NotNull Long categoryId,
        @NotNull Long serviceId,
        @Min(1) @Max(3) short impact,
        @Min(1) @Max(3) short urgency,
        @Min(1) int affectedUsers) { }
