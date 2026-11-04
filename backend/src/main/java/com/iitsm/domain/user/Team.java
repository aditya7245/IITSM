package com.iitsm.domain.user;

import com.iitsm.domain.common.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "teams")
public class Team extends BaseEntity {

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lead_id")
    private TeamLead lead;

    protected Team() { }

    public Team(String name) { this.name = name; }

    public String getName() { return name; }
    public TeamLead getLead() { return lead; }
    public void assignLead(TeamLead lead) { this.lead = lead; }
}
