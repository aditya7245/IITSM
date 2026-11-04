package com.iitsm.domain.user;

import jakarta.persistence.*;

/** A team lead IS-A technician who is also the first escalation step for their team. */
@Entity
@DiscriminatorValue("TEAM_LEAD")
public class TeamLead extends Technician {

    protected TeamLead() { }

    public TeamLead(String email, String passwordHash, String fullName, Team team, int maxWorkload, int experienceYears) {
        super(email, passwordHash, fullName, team, maxWorkload, experienceYears);
    }

    @Override public Role getRole() { return Role.TEAM_LEAD; }
}
