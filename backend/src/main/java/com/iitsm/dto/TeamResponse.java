package com.iitsm.dto;

/** A team and its lead ({@code lead} is null until one is chosen). */
public record TeamResponse(Long id, String name, NamedReference lead) { }
