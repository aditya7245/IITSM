package com.iitsm.security;

import com.iitsm.domain.common.BaseEntity;
import com.iitsm.domain.user.Employee;
import com.iitsm.domain.user.User;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.repository.UserRepository;
import com.iitsm.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/** Puts a logged-in user into the security context by hand, the way the JWT filter does for a real request. */
@Transactional
class CurrentUserServiceTest extends AbstractIntegrationTest {

    @Autowired CurrentUserService currentUser;
    @Autowired UserRepository users;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private void logInAs(UserPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void returnsTheLoggedInUser() {
        User tech = users.findByEmailIgnoreCase("tech1@iitsm.local").orElseThrow();
        logInAs(new UserPrincipal(tech));

        User found = currentUser.requireUser();

        assertEquals(tech.getId(), found.getId());
        assertEquals("tech1@iitsm.local", found.getEmail());
    }

    @Test
    void nobodyLoggedInIsRefused() {
        assertThrows(UnauthorizedOperationException.class, () -> currentUser.requireUser());
    }

    @Test
    void anAnonymousLoginIsRefused() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null));
        assertThrows(UnauthorizedOperationException.class, () -> currentUser.requireUser());
    }

    @Test
    void aDeactivatedAccountIsRefused() {
        User emp = users.findByEmailIgnoreCase("emp2@iitsm.local").orElseThrow();
        logInAs(new UserPrincipal(emp));
        emp.deactivate();
        users.saveAndFlush(emp);

        assertThrows(UnauthorizedOperationException.class, () -> currentUser.requireUser());
    }

    @Test
    void anAccountThatNoLongerExistsIsRefused() throws Exception {
        Employee ghost = new Employee("ghost@iitsm.local", "hash", "Ghost", "Sales");
        Field f = BaseEntity.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(ghost, 999_999_999L);
        logInAs(new UserPrincipal(ghost));

        assertThrows(UnauthorizedOperationException.class, () -> currentUser.requireUser());
    }

    @Test
    void aPrincipalWithoutAnIdIsRefused() {
        logInAs(new UserPrincipal(new Employee("new@iitsm.local", "hash", "New", "Sales")));
        assertThrows(UnauthorizedOperationException.class, () -> currentUser.requireUser());
    }
}
