package com.iitsm;

import com.iitsm.exception.DuplicateResourceException;
import com.iitsm.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExceptionStatusTest {

    @Test
    void invalidInputMapsToBadRequest() {
        InvalidInputException e = new InvalidInputException("title is required");
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("title is required", e.getMessage());
    }

    @Test
    void duplicateResourceMapsToConflict() {
        DuplicateResourceException e = new DuplicateResourceException("email already registered");
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("email already registered", e.getMessage());
    }
}
