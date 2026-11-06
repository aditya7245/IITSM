package com.iitsm.domain.catalog;

import jakarta.persistence.*;

/** A technical skill a technician can have, for example "Networking". */
@Entity
@Table(name = "skills")
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    protected Skill() { }

    public Skill(String name) {
        this.name = name;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
}
