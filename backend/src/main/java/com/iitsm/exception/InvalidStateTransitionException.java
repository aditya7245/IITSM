package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends IitsmException {
    public InvalidStateTransitionException(String from, String to) {
        super(HttpStatus.CONFLICT, "Cannot move incident from " + from + " to " + to);
    }
}
