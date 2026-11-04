package com.iitsm.exception;

import org.springframework.http.HttpStatus;

public class WorkloadExceededException extends IitsmException {
    public WorkloadExceededException(String name, int max) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "Technician " + name + " has reached the workload limit of " + max);
    }
}
