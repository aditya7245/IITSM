package com.iitsm.domain.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key of technician_skills: one row per (technician, skill) pair. */
@Embeddable
public class TechnicianSkillId implements Serializable {

    @Column(name = "technician_id", nullable = false)
    private Long technicianId;

    @Column(name = "skill_id", nullable = false)
    private Long skillId;

    protected TechnicianSkillId() { }

    public TechnicianSkillId(Long technicianId, Long skillId) {
        this.technicianId = technicianId;
        this.skillId = skillId;
    }

    public Long getTechnicianId() { return technicianId; }
    public Long getSkillId() { return skillId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TechnicianSkillId other)) return false;
        return Objects.equals(technicianId, other.technicianId) && Objects.equals(skillId, other.skillId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(technicianId, skillId);
    }
}
