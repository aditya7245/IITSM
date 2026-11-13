package com.iitsm.dto;

/** An IT service. {@code businessCriticality} runs from 1 (low) to 5 (vital). */
public record ServiceResponse(Long id, String name, String description, int businessCriticality, boolean active) { }
