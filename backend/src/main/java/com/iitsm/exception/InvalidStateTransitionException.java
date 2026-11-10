package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class InvalidStateTransitionException extends IitsmException {
    public InvalidStateTransitionException(String from, String to) {
        super(HttpStatus.CONFLICT, "Cannot move incident from " + from + " to " + to);
    }

    /** For actions that are not a move between statuses, such as commenting on a finished incident. */
    public InvalidStateTransitionException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
