package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class MissingSlaConfigurationException extends IitsmException {
    public MissingSlaConfigurationException(String priority) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, "No SLA policy configured for priority " + priority);
    }
}
