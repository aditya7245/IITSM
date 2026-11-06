package com.iitsm.exception;

import org.springframework.http.HttpStatus;

/** Creating something that must be unique but already exists, such as an email address (HTTP 409). */
public class DuplicateResourceException extends IitsmException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
