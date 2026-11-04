package com.iitsm.domain.user;

import com.iitsm.domain.common.BaseEntity;
import jakarta.persistence.*;

/**
 * Abstract root of the user hierarchy (single-table inheritance, discriminator column "role").
 * Subclass-specific columns are nullable in the table and only mapped on the subclass.
 */
@Entity
@Table(name = "users")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "role", discriminatorType = DiscriminatorType.STRING, length = 30)
public abstract class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false)
    private boolean active = true;

    protected User() { }  // JPA

    protected User(String email, String passwordHash, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }

    /** Each concrete subclass declares its own role (polymorphism instead of a type switch). */
    public abstract Role getRole();

    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public boolean isActive() { return active; }

    public void changePasswordHash(String newHash) { this.passwordHash = newHash; }
    public void rename(String fullName) { this.fullName = fullName; }
    public void deactivate() { this.active = false; }
    public void activate() { this.active = true; }
}
