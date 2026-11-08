package com.iitsm.security;

import com.iitsm.domain.user.User;
import com.iitsm.exception.UnauthorizedOperationException;
import com.iitsm.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds out who is making the current request, as a full {@link User} from the database. */
@Service
public class CurrentUserService {

    private final UserRepository users;

    public CurrentUserService(UserRepository users) {
        this.users = users;
    }

    /**
     * @return the logged-in user. Called inside a service method's transaction, the returned user belongs to
     *         that same transaction.
     * @throws UnauthorizedOperationException if nobody is logged in, or the account no longer exists or is inactive
     */
    @Transactional(readOnly = true)
    public User requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)
                || principal.getId() == null) {
            throw new UnauthorizedOperationException();
        }
        return users.findById(principal.getId())
                .filter(User::isActive)
                .orElseThrow(UnauthorizedOperationException::new);
    }
}
