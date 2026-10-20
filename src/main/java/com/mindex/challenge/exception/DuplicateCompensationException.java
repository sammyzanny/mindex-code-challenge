package com.mindex.challenge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.time.LocalDate;

/**
 * Raised when an employee already has a compensation record for the requested effectiveDate.
 * Mapped to HTTP 409 because the request is well formed but conflicts with stored data.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateCompensationException extends RuntimeException {

    public DuplicateCompensationException(String employeeId, LocalDate effectiveDate) {
        super("Employee " + employeeId + " already has a compensation record effective " + effectiveDate);
    }
}
