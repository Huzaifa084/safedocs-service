package org.devaxiom.safedocs.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(Long transactionId) {
        super(String.format("Transaction with ID %d not found", transactionId));
    }

    public TransactionNotFoundException(String message) {
        super(message);
    }
}
