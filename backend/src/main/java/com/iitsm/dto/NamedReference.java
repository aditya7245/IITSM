package com.iitsm.dto;

/** A short pointer to something else: its id and its name (a person's full name, a service name, a category name). */
public record NamedReference(Long id, String name) { }
