package org.devaxiom.safedocs.dto.document;

import org.devaxiom.safedocs.enums.DocumentShareStatus;

public record DocumentShareResponse(
        Long id,
        String recipientEmail,
        boolean canEdit,
        DocumentShareStatus status
) {
}
