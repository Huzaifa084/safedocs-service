package org.devaxiom.safedocs.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when creating/seeding a resource that already exists (e.g., re-seeding a permission).
 */
@Getter
@ResponseStatus(HttpStatus.CONFLICT)
public class ResourceAlreadyExistsException extends RuntimeException {
    private final String resource;   // e.g., "Permission"
    private final String field;      // e.g., "code"
    private final String value;      // e.g., "TICKET_CREATE"

    public ResourceAlreadyExistsException(String resource, String field, String value) {
        super(String.format("%s with %s '%s' already exists", resource, field, value));
        this.resource = resource;
        this.field = field;
        this.value = value;
    }

    public ResourceAlreadyExistsException(String message) {
        super(message);
        this.resource = null;
        this.field = null;
        this.value = null;
    }

}
