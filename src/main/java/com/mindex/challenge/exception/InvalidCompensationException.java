package com.mindex.challenge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a compensation request is missing a required field or holds a value that can
 * never be valid. Mapped to HTTP 400 so the client knows the request itself must change.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCompensationException extends RuntimeException {

    public InvalidCompensationException(String message) {
        super(message);
    }
}
