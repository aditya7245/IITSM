package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class DuplicateIncidentException extends IitsmException {
    public DuplicateIncidentException(String existingNumber) {
        super(HttpStatus.CONFLICT, "Possible duplicate of incident " + existingNumber);
    }

    private DuplicateIncidentException(HttpStatus status, String message) {
        super(status, message);
    }

    /**
     * Refuses to link an incident to one that is itself a duplicate, which would build chains (and allow cycles);
     * the caller is told which incident to link to instead.
     */
    public static DuplicateIncidentException chain(String candidateNumber, String rootNumber) {
        return new DuplicateIncidentException(HttpStatus.CONFLICT, candidateNumber + " is itself a duplicate of "
                + rootNumber + "; mark it as a duplicate of " + rootNumber + " instead");
    }
}
