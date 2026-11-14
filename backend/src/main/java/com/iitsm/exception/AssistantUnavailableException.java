package com.iitsm.exception;

import org.springframework.http.HttpStatus;

/** The AI guide is switched off, or its provider failed or did not answer in time (HTTP 503). */
public class AssistantUnavailableException extends IitsmException {
    public AssistantUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
