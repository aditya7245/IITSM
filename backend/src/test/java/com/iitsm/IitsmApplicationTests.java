package com.iitsm;

import com.iitsm.domain.user.*;
import com.iitsm.repository.UserRepository;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/** Smoke test: Flyway migrations apply, Hibernate validates the mapping, and the user hierarchy loads polymorphically. */
class IitsmApplicationTests extends AbstractIntegrationTest {

    @Autowired UserRepository users;

    @Test
    void contextLoadsAndSeedUsersMapToSubclasses() {
        assertInstanceOf(Administrator.class, users.findByEmailIgnoreCase("admin@iitsm.local").orElseThrow());
        assertInstanceOf(ITManager.class, users.findByEmailIgnoreCase("manager@iitsm.local").orElseThrow());
        User lead = users.findByEmailIgnoreCase("lead.app@iitsm.local").orElseThrow();
        assertInstanceOf(TeamLead.class, lead);
        assertInstanceOf(Technician.class, lead);   // team lead IS-A technician
        assertInstanceOf(Employee.class, users.findByEmailIgnoreCase("emp1@iitsm.local").orElseThrow());
    }
}
