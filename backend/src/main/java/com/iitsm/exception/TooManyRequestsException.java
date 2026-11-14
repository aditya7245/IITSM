package com.iitsm.exception;

import org.springframework.http.HttpStatus;

/** The caller made too many requests of one kind in a short time, for example failed logins (HTTP 429). */
public class TooManyRequestsException extends IitsmException {
    public TooManyRequestsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
