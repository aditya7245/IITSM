package com.iitsm.domain.catalog;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.*;

/** How good a technician is at a skill, from 1 (beginner) to 5 (expert). */
@Entity
@Table(name = "technician_skills")
public class TechnicianSkill {

    @EmbeddedId
    private TechnicianSkillId id;

    @Column(nullable = false)
    private short proficiency;

    protected TechnicianSkill() { }

    public TechnicianSkill(Long technicianId, Long skillId, short proficiency) {
        if (proficiency < 1 || proficiency > 5) {
            throw new InvalidInputException("Proficiency must be between 1 and 5");
        }
        this.id = new TechnicianSkillId(technicianId, skillId);
        this.proficiency = proficiency;
    }

    public TechnicianSkillId getId() { return id; }
    public Long getTechnicianId() { return id.getTechnicianId(); }
    public Long getSkillId() { return id.getSkillId(); }
    public short getProficiency() { return proficiency; }
}
