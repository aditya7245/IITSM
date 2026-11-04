package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class DuplicateIncidentException extends IitsmException {
    public DuplicateIncidentException(String existingNumber) {
        super(HttpStatus.CONFLICT, "Possible duplicate of incident " + existingNumber);
    }
}
