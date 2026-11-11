package com.iitsm.exception;

import org.springframework.http.HttpStatus;

/** Something the caller asked for does not exist (HTTP 404). The message says what. */
public class ResourceNotFoundException extends IitsmException {
    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
