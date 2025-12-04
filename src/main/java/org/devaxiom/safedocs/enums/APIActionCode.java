package org.devaxiom.safedocs.enums;

public enum APIActionCode {
    // Validation/client errors
    VAL400, // validation failed
    ATH401, // authentication required
    FOR403, // forbidden
    NFD404, // not found
    DUP409, // duplicate
    BAD400, // bad request
    TOO_MANY_429, // too many requests
    UN_AUTH401, // unauthorized, revoked token
    SRV503, // service unavailable
    SRV504, // service timeout

    // Domain‐specific
    NFA568, // insufficient funds

    // Server‐side
    SRV500,  // internal server error
    ERR, // generic error
}