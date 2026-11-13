package com.iitsm.controller;

import com.iitsm.dto.LoginRequest;
import com.iitsm.dto.LoginResponse;
import com.iitsm.dto.RegisterRequest;
import com.iitsm.dto.UserResponse;
import com.iitsm.service.AuthService;
import com.iitsm.service.UserAdminFacade;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UserAdminFacade accounts;

    public AuthController(AuthService auth, UserAdminFacade accounts) {
        this.auth = auth;
        this.accounts = accounts;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /** Public sign-up. Always creates an employee; an email that is already used is a 409. Log in afterwards to get a token. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return accounts.register(request);
    }

    /** The logged-in user's own account. */
    @GetMapping("/me")
    public UserResponse me() {
        return accounts.me();
    }
}
