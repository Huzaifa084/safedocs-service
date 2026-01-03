package org.devaxiom.safedocs.dto.document;

import org.devaxiom.safedocs.enums.DocumentAccessLevel;
import org.devaxiom.safedocs.enums.DocumentReferenceType;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.enums.StorageProvider;

import java.util.UUID;

public record UpdateDocumentRequest(
        String title,
        String category,
        DocumentVisibility visibility,
        UUID familyId,
        String driveFileId,
        String fileName,
        DocumentReferenceType referenceType,
        StorageProvider storageProvider,
        DocumentAccessLevel accessLevel
) {
}
