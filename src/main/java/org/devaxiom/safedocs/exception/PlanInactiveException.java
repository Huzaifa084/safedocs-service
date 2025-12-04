package org.devaxiom.safedocs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PlanInactiveException extends RuntimeException {
    public PlanInactiveException() {
        super("No active subscription found");
    }
}
