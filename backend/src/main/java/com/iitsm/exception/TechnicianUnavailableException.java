package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class TechnicianUnavailableException extends IitsmException {
    public TechnicianUnavailableException(String reason) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "No eligible technician available: " + reason);
    }
}
