package com.iitsm.domain;

import com.iitsm.domain.catalog.Category;
import com.iitsm.domain.catalog.ITService;
import com.iitsm.domain.catalog.Skill;
import com.iitsm.domain.incident.Priority;
import com.iitsm.domain.sla.SlaPolicy;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.Team;
import com.iitsm.domain.user.Technician;
import com.iitsm.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Plain unit tests of the methods the administration endpoints use to change entities, including their rules. */
class EntityChangesTest {

    // ---------- users ----------

    @Test
    void anEmployeesDepartmentCanChange() {
        Employee employee = new Employee("e@iitsm.local", "h", "Emma", "Finance");

        employee.changeDepartment("HR");
        assertEquals("HR", employee.getDepartment());
        employee.changeDepartment(null);
        assertNull(employee.getDepartment());
    }

    @Test
    void aTechnicianCanChangeLimitExperienceAndTeam() {
        Team infrastructure = new Team("Infrastructure");
        Technician technician = new Technician("t@iitsm.local", "h", "Tom", null, 5, 4);

        technician.changeMaxWorkload(2);
        technician.changeExperienceYears(9);
        technician.moveToTeam(infrastructure);

        assertEquals(2, technician.getMaxWorkload());
        assertEquals(9, technician.getExperienceYears());
        assertSame(infrastructure, technician.getTeam());
        technician.moveToTeam(null);
        assertNull(technician.getTeam());
    }

    @Test
    void theWorkloadLimitMustBeAtLeastOneAndExperienceCannotBeNegative() {
        Technician technician = new Technician("t@iitsm.local", "h", "Tom", null, 5, 4);

        assertThrows(InvalidInputException.class, () -> technician.changeMaxWorkload(0));
        assertThrows(InvalidInputException.class, () -> technician.changeExperienceYears(-1));
        assertEquals(5, technician.getMaxWorkload());                 // unchanged after a refusal
        assertEquals(4, technician.getExperienceYears());
        technician.changeMaxWorkload(1);
        technician.changeExperienceYears(0);
        assertEquals(1, technician.getMaxWorkload());
        assertEquals(0, technician.getExperienceYears());
    }

    // ---------- the catalog ----------

    @Test
    void aServiceCanBeChangedAndSwitchedOffAndOn() {
        ITService service = new ITService("Wiki", "Docs", (short) 1);

        service.update("Internal Wiki", "Documentation", (short) 2);
        assertEquals("Internal Wiki", service.getName());
        assertEquals("Documentation", service.getDescription());
        assertEquals(2, service.getBusinessCriticality());
        service.deactivate();
        assertFalse(service.isActive());
        service.activate();
        assertTrue(service.isActive());
    }

    @Test
    void aServiceKeepsItsOldValuesWhenTheNewCriticalityIsInvalid() {
        ITService service = new ITService("Wiki", "Docs", (short) 1);

        assertThrows(InvalidInputException.class, () -> service.update("Changed", "Changed", (short) 6));
        assertThrows(InvalidInputException.class, () -> service.update("Changed", "Changed", (short) 0));

        assertEquals("Wiki", service.getName());
        assertEquals(1, service.getBusinessCriticality());
    }

    @Test
    void aCategoryCanBeChangedAndSwitchedOffAndOn() {
        Skill networking = new Skill("Networking");
        Category category = new Category("Network", "Network problems", "wifi,vpn", null);

        category.update("Connectivity", "Connection problems", "wifi,lan", networking);
        assertEquals("Connectivity", category.getName());
        assertEquals("wifi,lan", category.getKeywords());
        assertSame(networking, category.getRequiredSkill());
        category.update("Connectivity", null, null, null);
        assertEquals("", category.getKeywords());                     // no keywords is an empty list, not null
        assertNull(category.getRequiredSkill());
        category.deactivate();
        assertFalse(category.isActive());
        category.activate();
        assertTrue(category.isActive());
    }

    @Test
    void aPolicyChangesItsTimesButKeepsItsPriorityAndService() {
        ITService payment = new ITService("Payment", "p", (short) 5);
        SlaPolicy policy = new SlaPolicy(Priority.P1, payment, 5, 30);

        policy.changeMinutes(2, 10);

        assertEquals(2, policy.getResponseMinutes());
        assertEquals(10, policy.getResolutionMinutes());
        assertEquals(Priority.P1, policy.getPriority());
        assertSame(payment, policy.getService());
    }

    @Test
    void policyTimesMustBePositiveAndAreKeptWhenRefused() {
        SlaPolicy policy = new SlaPolicy(Priority.P2, null, 15, 120);

        assertThrows(InvalidInputException.class, () -> policy.changeMinutes(0, 10));
        assertThrows(InvalidInputException.class, () -> policy.changeMinutes(10, 0));
        assertThrows(InvalidInputException.class, () -> policy.changeMinutes(-1, -1));

        assertEquals(15, policy.getResponseMinutes());
        assertEquals(120, policy.getResolutionMinutes());
    }
}
