package org.devaxiom.safedocs.exception;

import lombok.Getter;

@Getter
public class PreAuthRequiredException extends RuntimeException {
    private final String preAuthToken;

    public PreAuthRequiredException(String preAuthToken) {
        super("Pre-authentication required");
        this.preAuthToken = preAuthToken;
    }

}
