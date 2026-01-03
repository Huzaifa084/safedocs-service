package org.devaxiom.safedocs.dto.document;

import org.devaxiom.safedocs.enums.DocumentAccessLevel;
import org.devaxiom.safedocs.enums.DocumentReferenceType;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record DocumentResponse(
        UUID publicId,
        String driveFileId,
        String fileName,
        String title,
        String mimeType,
        Long sizeBytes,
        DocumentVisibility visibility,
        String category,
        UUID familyId,
        DocumentReferenceType referenceType,
        DocumentStatus status,
        Instant driveCreatedAt,
        String driveWebViewLink,
        String driveMd5,
        DocumentAccessLevel accessLevel,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
