package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class ServiceNotFoundException extends IitsmException {
    public ServiceNotFoundException(String ref) {
        super(HttpStatus.NOT_FOUND, "IT service not found: " + ref);
    }
}
