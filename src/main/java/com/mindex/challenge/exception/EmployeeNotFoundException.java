package com.mindex.challenge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when an employeeId does not match any stored employee. Mapped to HTTP 404 so clients
 * can tell a missing employee apart from a server fault.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(String employeeId) {
        super("Invalid employeeId: " + employeeId);
    }
}
