package com.iitsm.domain.user;

import com.iitsm.exception.InvalidInputException;
import com.iitsm.exception.WorkloadExceededException;
import jakarta.persistence.*;

@Entity
@DiscriminatorValue("TECHNICIAN")
public class Technician extends User {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "max_workload")
    private int maxWorkload = 5;

    @Column(name = "available")
    private boolean available = true;

    @Column(name = "experience_years")
    private int experienceYears;

    protected Technician() { }

    public Technician(String email, String passwordHash, String fullName, Team team, int maxWorkload, int experienceYears) {
        super(email, passwordHash, fullName);
        this.team = team;
        this.maxWorkload = maxWorkload;
        this.experienceYears = experienceYears;
    }

    @Override public Role getRole() { return Role.TECHNICIAN; }

    public Team getTeam() { return team; }
    public int getMaxWorkload() { return maxWorkload; }
    public boolean isAvailable() { return available; }
    public int getExperienceYears() { return experienceYears; }

    public void setAvailable(boolean available) { this.available = available; }

    public void changeMaxWorkload(int maxWorkload) {
        if (maxWorkload < 1) {
            throw new InvalidInputException("Maximum workload must be at least 1");
        }
        this.maxWorkload = maxWorkload;
    }

    public void changeExperienceYears(int experienceYears) {
        if (experienceYears < 0) {
            throw new InvalidInputException("Experience cannot be negative");
        }
        this.experienceYears = experienceYears;
    }

    /** Moves the technician to another team, or out of any team when {@code team} is null. */
    public void moveToTeam(Team team) { this.team = team; }

    public boolean hasCapacity(int activeIncidents) {
        return available && activeIncidents < maxWorkload;
    }

    /** Business rule guard used by assignment code. */
    public void requireCapacity(int activeIncidents) {
        if (activeIncidents >= maxWorkload) {
            throw new WorkloadExceededException(getFullName(), maxWorkload);
        }
    }
}
