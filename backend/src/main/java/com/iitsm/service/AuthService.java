package com.iitsm.service;

import com.iitsm.dto.LoginRequest;
import com.iitsm.dto.LoginResponse;
import com.iitsm.exception.InvalidCredentialsException;
import com.iitsm.security.JwtService;
import com.iitsm.security.RateLimiter;
import com.iitsm.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;

@Service
public class AuthService {

    /** After this many failed logins for one e-mail address within the window, further attempts are refused. */
    static final int MAX_FAILED_LOGINS = 10;
    static final Duration FAILED_LOGIN_WINDOW = Duration.ofMinutes(15);

    private final AuthenticationManager authManager;
    private final JwtService jwt;
    private final RateLimiter failedLogins;

    public AuthService(AuthenticationManager authManager, JwtService jwt, Clock clock) {
        this.authManager = authManager;
        this.jwt = jwt;
        this.failedLogins = new RateLimiter(MAX_FAILED_LOGINS, FAILED_LOGIN_WINDOW, clock, "failed login attempts");
    }

    /**
     * Checks the password and issues a token. Password guessing is slowed down: after {@value #MAX_FAILED_LOGINS}
     * failures for an address within 15 minutes, that address is refused (429) until the oldest failure expires.
     */
    public LoginResponse login(LoginRequest req) {
        failedLogins.check(req.email());
        try {
            var auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email(), req.password()));
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            failedLogins.reset(req.email());
            return new LoginResponse(jwt.generate(principal), "Bearer", jwt.ttlSeconds(), principal.getRole(), principal.getUsername());
        } catch (AuthenticationException e) {
            failedLogins.record(req.email());
            throw new InvalidCredentialsException();
        }
    }
}
