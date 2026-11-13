package com.iitsm.dto;

/** An incident category. {@code keywords} is a comma-separated list; {@code requiredSkill} is null if no skill is needed. */
public record CategoryResponse(Long id, String name, String description, String keywords, NamedReference requiredSkill,
                               boolean active) { }
