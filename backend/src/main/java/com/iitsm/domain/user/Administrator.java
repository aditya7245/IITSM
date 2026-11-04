package com.iitsm.domain.user;

import jakarta.persistence.*;

@Entity
@DiscriminatorValue("ADMINISTRATOR")
public class Administrator extends User {

    protected Administrator() { }

    public Administrator(String email, String passwordHash, String fullName) {
        super(email, passwordHash, fullName);
    }

    @Override public Role getRole() { return Role.ADMINISTRATOR; }
}
