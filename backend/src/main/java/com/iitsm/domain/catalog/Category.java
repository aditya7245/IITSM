package com.iitsm.domain.catalog;

import jakarta.persistence.*;

/**
 * An incident category. New categories are just new rows, so adding one needs no code change.
 * {@code keywords} is a comma-separated list of single words used by the category suggester.
 */
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 80)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private String keywords = "";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "required_skill_id")
    private Skill requiredSkill;

    @Column(nullable = false)
    private boolean active = true;

    protected Category() { }

    public Category(String name, String description, String keywords, Skill requiredSkill) {
        this.name = name;
        this.description = description;
        this.keywords = keywords == null ? "" : keywords;
        this.requiredSkill = requiredSkill;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getKeywords() { return keywords; }
    public Skill getRequiredSkill() { return requiredSkill; }
    public boolean isActive() { return active; }
}
