package org.devaxiom.safedocs.dto.document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public record ReplaceDocumentFileRequest(
        @NotBlank(message = "driveFileId is required")
        String driveFileId,
        @NotBlank(message = "fileName is required")
        String fileName,
        String mimeType,
        @PositiveOrZero(message = "sizeBytes must be >= 0")
        Long sizeBytes,
        Instant driveCreatedAt,
        String driveWebViewLink,
        String driveMd5
) {
}
