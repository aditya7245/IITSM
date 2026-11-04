package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends IitsmException {
    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
