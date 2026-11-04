package com.iitsm.exception;

import org.springframework.http.HttpStatus;

/** Base type for all business errors; carries the HTTP status the API layer should return. */
public abstract class IitsmException extends RuntimeException {

    private final HttpStatus status;

    protected IitsmException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
