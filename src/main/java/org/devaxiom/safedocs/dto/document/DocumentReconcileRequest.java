package org.devaxiom.safedocs.dto.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record DocumentReconcileRequest(
        @NotEmpty(message = "missing list is required")
        List<@Valid MissingDocument> missing
) {
    public record MissingDocument(
            UUID publicId,
            String driveFileId,
            String reason
    ) {
    }
}
