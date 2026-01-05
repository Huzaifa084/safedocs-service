package org.devaxiom.safedocs.dto.document;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkUpdateDocumentSubjectRequest(
        @NotEmpty(message = "documentIds is required")
        List<UUID> documentIds,
        UUID subjectId
) {
}
