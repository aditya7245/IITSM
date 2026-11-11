package com.iitsm.support;

import com.iitsm.domain.user.User;
import com.iitsm.repository.UserRepository;
import com.iitsm.security.JwtService;
import com.iitsm.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

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

    /** The response body as text. */
    protected String body(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }
}
