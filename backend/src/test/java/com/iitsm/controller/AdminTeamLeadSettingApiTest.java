package com.iitsm.controller;

import com.iitsm.support.AbstractApiTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The switch that lets team leads receive new incidents automatically, through the real security chain. */
class AdminTeamLeadSettingApiTest extends AbstractApiTest {

    private static final String URL = "/api/admin/config/team-lead-assignment";

    private boolean enabled() throws Exception {
        return flag(body(getAs("admin", URL).andExpect(status().isOk())));
    }

    @Test
    void teamLeadsAreOffByDefaultAndTheSwitchCanBeTurnedOnAndOffAgain() throws Exception {
        assertFalse(enabled());

        assertTrue(flag(body(putAs("admin", URL, "{\"enabled\": true}").andExpect(status().isOk()))));
        assertTrue(enabled());

        assertFalse(flag(body(putAs("manager", URL, "{\"enabled\": false}").andExpect(status().isOk()))));
        assertFalse(enabled());
    }

    @Test
    void aMissingOrWrongValueIsA400AndTheSettingStays() throws Exception {
        putAs("admin", URL, "{\"enabled\": true}").andExpect(status().isOk());

        putAs("admin", URL, "{}").andExpect(status().isBadRequest());
        putAs("admin", URL, "{\"enabled\": \"maybe\"}").andExpect(status().isBadRequest());

        assertTrue(enabled());
    }

    @Test
    void onlyAdministratorsMayReadOrChangeIt() throws Exception {
        for (String user : new String[] {"emp1", "tech1", "lead.app"}) {
            getAs(user, URL).andExpect(status().isForbidden());
            putAs(user, URL, "{\"enabled\": true}").andExpect(status().isForbidden());
        }
        getAs(null, URL).andExpect(status().isUnauthorized());
        assertFalse(enabled());
    }

    /** The {@code enabled} value of a response. */
    private static boolean flag(String json) {
        return JsonPath.read(json, "$.enabled");
    }
}
