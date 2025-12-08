package org.devaxiom.safedocs.dto.document;

import org.devaxiom.safedocs.enums.DocumentVisibility;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String title,
        String category,
        DocumentVisibility visibility,
        LocalDate expiryDate,
        String storageKey,
        String storageFilename,
        Long storageSizeBytes,
        String mimeType,
        String ownerName,
        UUID familyId,
        String familyName
) {
}
