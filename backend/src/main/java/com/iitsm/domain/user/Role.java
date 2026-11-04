package com.iitsm.domain.user;

import java.util.List;

/** Discriminator values for the users table. A role may carry the authorities of its parent role. */
public enum Role {
    EMPLOYEE("ROLE_EMPLOYEE"),
    TECHNICIAN("ROLE_TECHNICIAN"),
    TEAM_LEAD("ROLE_TECHNICIAN", "ROLE_TEAM_LEAD"),     // a team lead is a technician
    ADMINISTRATOR("ROLE_ADMIN"),
    IT_MANAGER("ROLE_ADMIN", "ROLE_IT_MANAGER");        // an IT manager is an administrator

    private final List<String> authorities;

    Role(String... authorities) {
        this.authorities = List.of(authorities);
    }

    public List<String> authorities() {
        return authorities;
    }
}
