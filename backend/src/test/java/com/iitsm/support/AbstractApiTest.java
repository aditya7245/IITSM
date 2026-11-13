package com.iitsm.support;

import com.iitsm.domain.user.User;
import com.iitsm.repository.CategoryRepository;
import com.iitsm.repository.ITServiceRepository;
import com.iitsm.repository.UserRepository;
import com.iitsm.security.JwtService;
import com.iitsm.security.UserPrincipal;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for tests of the REST API. Requests go through the real security filter chain with a real JWT,
 * created exactly as at login, for any of the seeded users (given by the part of the email before @iitsm.local,
 * such as {@code emp1}, {@code tech1}, {@code admin}). Pass {@code null} as the user to send no token at all.
 * Each test is rolled back, like the other integration tests.
 */
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractApiTest extends AbstractIntegrationTest {

    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected MutableClock clock;
    @Autowired private JwtService jwt;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ITServiceRepository serviceRepository;

    @BeforeEach
    void resetClock() {
        clock.reset();
    }

    protected User user(String name) {
        return users.findByEmailIgnoreCase(name + "@iitsm.local").orElseThrow();
    }

    /** A valid token for a seeded user, as the Authorization header value. */
    protected String bearer(String name) {
        return "Bearer " + jwt.generate(new UserPrincipal(user(name)));
    }

    protected ResultActions send(String name, MockHttpServletRequestBuilder request) throws Exception {
        if (name != null) {
            request.header(HttpHeaders.AUTHORIZATION, bearer(name));
        }
        return mvc.perform(request);
    }

    protected ResultActions getAs(String name, String url) throws Exception {
        return send(name, get(url));
    }

    /** A POST without a body, such as /acknowledge. */
    protected ResultActions postAs(String name, String url) throws Exception {
        return send(name, post(url));
    }

    protected ResultActions postAs(String name, String url, String json) throws Exception {
        return send(name, post(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions putAs(String name, String url, String json) throws Exception {
        return send(name, put(url).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    protected ResultActions deleteAs(String name, String url) throws Exception {
        return send(name, delete(url));
    }

    /** Logs in with an email and password (no token needed); the caller checks the status. */
    protected ResultActions loginWith(String email, String password) throws Exception {
        return send(null, post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    /** The response body as text. */
    protected String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ test data and reading JSON

    protected long categoryId(String name) {
        return categoryRepository.findByName(name).orElseThrow().getId();
    }

    protected long serviceId(String name) {
        return serviceRepository.findByName(name).orElseThrow().getId();
    }

    /** The user reports an incident through the API (it must succeed with 201) and gets its id back. */
    protected long submitIncident(String reporter, String title, String description, String category, String service,
                                  int impact, int urgency, int affectedUsers) throws Exception {
        String json = """
                {"title": "%s", "description": "%s", "categoryId": %d, "serviceId": %d,
                 "impact": %d, "urgency": %d, "affectedUsers": %d}
                """.formatted(title, description, categoryId(category), serviceId(service), impact, urgency, affectedUsers);
        return number(body(postAs(reporter, "/api/incidents", json).andExpect(status().isCreated())), "$.incident.id");
    }

    /** A text value from a JSON response, by JsonPath. */
    protected String text(String json, String path) {
        return JsonPath.read(json, path);
    }

    /** A whole-number value from a JSON response, by JsonPath. */
    protected long number(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    /** A list from a JSON response, by JsonPath (for example {@code $.content[*].incidentNumber}). */
    protected <T> List<T> list(String json, String path) {
        return JsonPath.read(json, path);
    }
}
