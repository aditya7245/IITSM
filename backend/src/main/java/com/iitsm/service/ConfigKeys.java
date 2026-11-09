package com.iitsm.service;

/** Names of the settings stored in the system_config table (seeded by migration V2). */
public final class ConfigKeys {

    public static final String ASSIGNMENT_STRATEGY = "assignment.strategy";
    public static final String DUPLICATE_THRESHOLD = "duplicate.threshold";
    public static final String DUPLICATE_WINDOW_HOURS = "duplicate.window.hours";
    public static final String PRIORITY_BUMP_USERS = "priority.bump.users";
    public static final String AUTO_ASSIGN_TEAM_LEADS = "assignment.auto.team.leads";

    private ConfigKeys() { }
}
