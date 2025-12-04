package org.devaxiom.safedocs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidStateException extends RuntimeException {

    public InvalidStateException(String message) {
        super(message);
    }

    public InvalidStateException(String entityName, Long entityId, String currentState) {
        super(String.format("%s with ID %d cannot be processed - current state: %s", entityName, entityId, currentState));
    }
}
