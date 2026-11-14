package com.iitsm.repository;

/** One line of a grouped count: something with an id and a name (a service, a category) and how many incidents belong to it. */
public record NamedCount(Long id, String name, Long count) { }
