package org.devaxiom.safedocs.dto.document;

import org.devaxiom.safedocs.enums.DocumentReferenceType;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;

import java.util.UUID;

public record DocumentListItem(
        UUID publicId,
        String driveFileId,
        String fileName,
        String title,
        String mimeType,
        Long sizeBytes,
        DocumentVisibility visibility,
        String category,
        UUID familyId,
        UUID subjectId,
        DocumentReferenceType referenceType,
        DocumentStatus status
) {
}
