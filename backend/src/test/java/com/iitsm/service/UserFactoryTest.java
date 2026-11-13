package com.iitsm.service;

import com.iitsm.domain.user.Administrator;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.ITManager;
import com.iitsm.domain.user.Role;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.TeamLead;
import com.iitsm.domain.user.Technician;
import com.iitsm.domain.user.User;
import com.iitsm.exception.InvalidInputException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Plain unit tests of {@link UserFactory}: the right class for every role, no plain password kept, and the
 * validation limits. The encoder is a hand-written fake that marks what it has hashed.
 */
class UserFactoryTest {

    static class FakeEncoder implements PasswordEncoder {
        @Override
        public String encode(CharSequence raw) {
            return "hashed:" + raw;
        }

        @Override
        public boolean matches(CharSequence raw, String encoded) {
            return encode(raw).equals(encoded);
        }
    }

    UserFactory factory;
    Team team;

    @BeforeEach
    void setUp() {
        factory = new UserFactory(new FakeEncoder());
        team = new Team("Infrastructure");
    }

    private CreateUserCommand command(Role role) {
        return new CreateUserCommand("new.person@iitsm.local", "Password@123", "New Person", role, "Finance", team, 4, 3);
    }

    // ---------- every role ----------

    @Test
    void anEmployeeHasADepartmentAndNoTechnicianFields() {
        User user = factory.create(command(Role.EMPLOYEE));

        Employee employee = assertInstanceOf(Employee.class, user);
        assertEquals(Role.EMPLOYEE, employee.getRole());
        assertEquals("Finance", employee.getDepartment());
        assertEquals("new.person@iitsm.local", employee.getEmail());
        assertEquals("New Person", employee.getFullName());
        assertTrue(employee.isActive());
    }

    @Test
    void aTechnicianGetsTheTeamWorkloadLimitAndExperience() {
        Technician technician = assertInstanceOf(Technician.class, factory.create(command(Role.TECHNICIAN)));

        assertEquals(Role.TECHNICIAN, technician.getRole());
        assertSame(team, technician.getTeam());
        assertEquals(4, technician.getMaxWorkload());
        assertEquals(3, technician.getExperienceYears());
        assertTrue(technician.isAvailable());
        assertFalse(technician instanceof TeamLead);
    }

    @Test
    void aTeamLeadIsATechnicianWithTheTeamLeadRole() {
        User user = factory.create(command(Role.TEAM_LEAD));

        TeamLead lead = assertInstanceOf(TeamLead.class, user);
        assertEquals(Role.TEAM_LEAD, lead.getRole());
        assertSame(team, lead.getTeam());
        assertEquals(4, lead.getMaxWorkload());
        assertEquals(3, lead.getExperienceYears());
    }

    @Test
    void anAdministratorAndAnITManagerAreDifferentClasses() {
        User administrator = factory.create(command(Role.ADMINISTRATOR));
        User manager = factory.create(command(Role.IT_MANAGER));

        assertEquals(Administrator.class, administrator.getClass());
        assertEquals(Role.ADMINISTRATOR, administrator.getRole());
        assertEquals(ITManager.class, manager.getClass());
        assertEquals(Role.IT_MANAGER, manager.getRole());
    }

    @Test
    void everyRoleGivesTheMatchingRoleBack() {
        for (Role role : Role.values()) {
            assertEquals(role, factory.create(command(role)).getRole(), "for " + role);
        }
    }

    // ---------- the password ----------

    @Test
    void thePlainPasswordIsNeverKept() {
        User user = factory.create(command(Role.EMPLOYEE));

        assertEquals("hashed:Password@123", user.getPasswordHash());
        assertNotEquals("Password@123", user.getPasswordHash());
    }

    @Test
    void thePasswordMustBeBetweenEightAndSeventyTwoCharacters() {
        assertDoesNotThrow(() -> factory.encodePassword("a".repeat(8)));
        assertDoesNotThrow(() -> factory.encodePassword("a".repeat(72)));
        assertThrows(InvalidInputException.class, () -> factory.encodePassword("a".repeat(7)));
        assertThrows(InvalidInputException.class, () -> factory.encodePassword("a".repeat(73)));
        assertThrows(InvalidInputException.class, () -> factory.encodePassword(""));
        assertThrows(InvalidInputException.class, () -> factory.encodePassword(null));
    }

    @Test
    void aBadPasswordStopsTheUserFromBeingCreated() {
        CreateUserCommand shortPassword = new CreateUserCommand("a@b.c", "short", "Name", Role.EMPLOYEE, null, null, 0, 0);

        assertThrows(InvalidInputException.class, () -> factory.create(shortPassword));
    }

    // ---------- the other details ----------

    @Test
    void spacesAroundTheTextAreRemovedAndABlankDepartmentIsNoDepartment() {
        CreateUserCommand spaced = new CreateUserCommand("  new@iitsm.local  ", "Password@123", "  New Person  ",
                Role.EMPLOYEE, "   ", null, 0, 0);

        Employee employee = (Employee) factory.create(spaced);

        assertEquals("new@iitsm.local", employee.getEmail());
        assertEquals("New Person", employee.getFullName());
        assertNull(employee.getDepartment());
    }

    @Test
    void aTechnicianMayHaveNoTeam() {
        CreateUserCommand noTeam = new CreateUserCommand("t@iitsm.local", "Password@123", "Tech", Role.TECHNICIAN, null, null, 5, 0);

        assertNull(((Technician) factory.create(noTeam)).getTeam());
    }

    @Test
    void theRoleIsRequired() {
        CreateUserCommand noRole = new CreateUserCommand("a@b.c", "Password@123", "Name", null, null, null, 0, 0);

        assertThrows(InvalidInputException.class, () -> factory.create(noRole));
    }

    @Test
    void theEmailMustLookLikeAnAddressAndFitTheColumn() {
        for (String bad : new String[]{null, "", "   ", "plainaddress", "@no-user.com", "no-domain@", "two words@x.com"}) {
            CreateUserCommand command = new CreateUserCommand(bad, "Password@123", "Name", Role.EMPLOYEE, null, null, 0, 0);
            assertThrows(InvalidInputException.class, () -> factory.create(command), "for " + bad);
        }
        String longEmail = "a".repeat(143) + "@iitsm.l";                  // 151 characters
        assertEquals(151, longEmail.length());
        assertThrows(InvalidInputException.class, () -> factory.create(
                new CreateUserCommand(longEmail, "Password@123", "Name", Role.EMPLOYEE, null, null, 0, 0)));
        assertDoesNotThrow(() -> factory.create(new CreateUserCommand("a".repeat(142) + "@iitsm.l", "Password@123", "Name",
                Role.EMPLOYEE, null, null, 0, 0)));                         // exactly 150
    }

    @Test
    void theNameAndDepartmentAreRequiredAndLimited() {
        assertThrows(InvalidInputException.class, () -> factory.create(
                new CreateUserCommand("a@b.c", "Password@123", " ", Role.EMPLOYEE, null, null, 0, 0)));
        assertThrows(InvalidInputException.class, () -> factory.create(
                new CreateUserCommand("a@b.c", "Password@123", "n".repeat(121), Role.EMPLOYEE, null, null, 0, 0)));
        assertDoesNotThrow(() -> factory.create(
                new CreateUserCommand("a@b.c", "Password@123", "n".repeat(120), Role.EMPLOYEE, null, null, 0, 0)));
        assertThrows(InvalidInputException.class, () -> factory.create(
                new CreateUserCommand("a@b.c", "Password@123", "Name", Role.EMPLOYEE, "d".repeat(101), null, 0, 0)));
    }

    @Test
    void technicianNumbersAreCheckedOnlyForTechniciansAndTeamLeads() {
        for (Role role : new Role[]{Role.TECHNICIAN, Role.TEAM_LEAD}) {
            assertThrows(InvalidInputException.class, () -> factory.create(
                    new CreateUserCommand("a@b.c", "Password@123", "Name", role, null, null, 0, 0)), "workload for " + role);
            assertThrows(InvalidInputException.class, () -> factory.create(
                    new CreateUserCommand("a@b.c", "Password@123", "Name", role, null, null, 5, -1)), "experience for " + role);
        }
        assertDoesNotThrow(() -> factory.create(
                new CreateUserCommand("a@b.c", "Password@123", "Name", Role.EMPLOYEE, null, null, 0, -1)));
        assertDoesNotThrow(() -> factory.create(
                new CreateUserCommand("a@b.c", "Password@123", "Name", Role.ADMINISTRATOR, null, null, 0, -1)));
    }
}
