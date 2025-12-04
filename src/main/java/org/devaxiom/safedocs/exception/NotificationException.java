package org.devaxiom.safedocs.exception;

import lombok.Getter;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@Setter
@Getter
@ResponseStatus(value = HttpStatus.UNAUTHORIZED)
public class NotificationException extends RuntimeException {
    private String message;

    public NotificationException(String message) {
        this.message = message;
    }

}
