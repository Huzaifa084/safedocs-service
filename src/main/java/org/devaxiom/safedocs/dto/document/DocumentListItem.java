package org.devaxiom.safedocs.dto.document;

import org.devaxiom.safedocs.enums.DocumentVisibility;

import java.time.LocalDate;
import java.util.UUID;

public record DocumentListItem(
        UUID id,
        String title,
        String category,
        DocumentVisibility visibility,
        LocalDate expiryDate,
        String ownerName
) {
}
