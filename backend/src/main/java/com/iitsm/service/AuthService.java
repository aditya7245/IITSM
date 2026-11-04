package com.iitsm.service;

import com.iitsm.dto.LoginRequest;
import com.iitsm.dto.LoginResponse;
import com.iitsm.exception.InvalidCredentialsException;
import com.iitsm.security.JwtService;
import com.iitsm.security.UserPrincipal;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final AuthenticationManager authManager;
    private final JwtService jwt;

    public AuthService(AuthenticationManager authManager, JwtService jwt) {
        this.authManager = authManager;
        this.jwt = jwt;
    }

    public LoginResponse login(LoginRequest req) {
        try {
            var auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(req.email(), req.password()));
            UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
            return new LoginResponse(jwt.generate(principal), "Bearer", jwt.ttlSeconds(), principal.getRole(), principal.getUsername());
        } catch (AuthenticationException e) {
            throw new InvalidCredentialsException();
        }
    }
}
