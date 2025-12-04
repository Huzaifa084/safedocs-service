package org.devaxiom.safedocs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class PlanLimitExceededException extends RuntimeException {
    public PlanLimitExceededException(String key) {
        super("Plan limit exceeded: " + key);
    }
}
