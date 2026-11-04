package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedOperationException extends IitsmException {
    public UnauthorizedOperationException() {
        super(HttpStatus.FORBIDDEN, "You are not allowed to perform this operation");
    }
}
