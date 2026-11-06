package com.iitsm.domain.catalog;

import com.iitsm.exception.InvalidInputException;
import jakarta.persistence.*;

/** An IT service that can fail, such as the payment application. Business criticality runs from 1 (low) to 5 (vital). */
@Entity
@Table(name = "it_services")
public class ITService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(length = 255)
    private String description;

    @Column(name = "business_criticality", nullable = false)
    private short businessCriticality;

    @Column(nullable = false)
    private boolean active = true;

    protected ITService() { }

    public ITService(String name, String description, short businessCriticality) {
        if (businessCriticality < 1 || businessCriticality > 5) {
            throw new InvalidInputException("Business criticality must be between 1 and 5");
        }
        this.name = name;
        this.description = description;
        this.businessCriticality = businessCriticality;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public short getBusinessCriticality() { return businessCriticality; }
    public boolean isActive() { return active; }
}
