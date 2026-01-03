package org.devaxiom.safedocs.dto.permission;

import org.devaxiom.safedocs.enums.PermissionJobAction;
import org.devaxiom.safedocs.enums.PermissionJobStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PermissionJobResponse(
        UUID jobId,
        UUID documentPublicId,
        String driveFileId,
        String targetUserEmail,
        PermissionJobAction action,
        UUID familyId,
        PermissionJobStatus status,
        int attempts,
        String lastError,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
