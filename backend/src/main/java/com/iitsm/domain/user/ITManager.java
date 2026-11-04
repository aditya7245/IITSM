package com.iitsm.domain.user;

import jakarta.persistence.*;

/** An IT manager IS-A administrator and is the last step of the escalation chain. */
@Entity
@DiscriminatorValue("IT_MANAGER")
public class ITManager extends Administrator {

    protected ITManager() { }

    public ITManager(String email, String passwordHash, String fullName) {
        super(email, passwordHash, fullName);
    }

    @Override public Role getRole() { return Role.IT_MANAGER; }
}
