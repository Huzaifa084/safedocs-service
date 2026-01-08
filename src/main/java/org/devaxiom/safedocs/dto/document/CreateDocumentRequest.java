package org.devaxiom.safedocs.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.devaxiom.safedocs.enums.DocumentAccessLevel;
import org.devaxiom.safedocs.enums.DocumentReferenceType;
import org.devaxiom.safedocs.enums.DocumentVisibility;

import java.time.Instant;
import java.util.UUID;

public record CreateDocumentRequest(
        @NotBlank(message = "driveFileId is required")
        String driveFileId,
        @NotBlank(message = "fileName is required")
        String fileName,
        String title,
        String mimeType,
        @PositiveOrZero(message = "sizeBytes must be >= 0")
        Long sizeBytes,
        @NotNull(message = "visibility is required")
        DocumentVisibility visibility,
        String category,
        UUID familyId,
        UUID subjectId,
        Instant driveCreatedAt,
        String driveWebViewLink,
        String driveMd5,
        DocumentAccessLevel accessLevel,
        DocumentReferenceType referenceType
) {
}
