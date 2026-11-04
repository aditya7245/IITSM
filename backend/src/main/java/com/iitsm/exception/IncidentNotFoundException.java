package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class IncidentNotFoundException extends IitsmException {
    public IncidentNotFoundException(String ref) {
        super(HttpStatus.NOT_FOUND, "Incident not found: " + ref);
    }
}
