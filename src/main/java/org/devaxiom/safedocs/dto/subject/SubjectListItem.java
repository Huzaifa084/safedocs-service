package org.devaxiom.safedocs.dto.subject;

import org.devaxiom.safedocs.enums.SubjectScope;

import java.time.LocalDateTime;
import java.util.UUID;

public record SubjectListItem(
        UUID id,
        String name,
        String semesterLabel,
        SubjectScope scope,
        UUID familyId,
        Long ownerUserId,
        long documentCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime lastDocumentActivityAt
) {
}
