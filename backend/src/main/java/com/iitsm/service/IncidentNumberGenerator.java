package com.iitsm.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Produces the next human-readable incident number, such as INC1001, from a database sequence. */
@Component
public class IncidentNumberGenerator {

    private final JdbcTemplate jdbc;

    public IncidentNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** A sequence never hands out the same number twice, even for concurrent requests. */
    public String next() {
        Long n = jdbc.queryForObject("select nextval('incident_number_seq')", Long.class);
        return "INC" + n;
    }
}
