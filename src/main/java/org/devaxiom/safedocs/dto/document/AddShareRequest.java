package org.devaxiom.safedocs.dto.document;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddShareRequest(
        @NotEmpty(message = "emails are required")
        List<String> emails
) {
}
