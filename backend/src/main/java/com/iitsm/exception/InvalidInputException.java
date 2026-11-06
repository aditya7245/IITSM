package com.iitsm.exception;

import org.springframework.http.HttpStatus;

/** The caller supplied data that breaks a business rule (HTTP 400). */
public class InvalidInputException extends IitsmException {
    public InvalidInputException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
