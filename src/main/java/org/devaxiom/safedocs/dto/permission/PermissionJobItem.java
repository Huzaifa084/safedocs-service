package org.devaxiom.safedocs.dto.permission;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.devaxiom.safedocs.enums.PermissionJobAction;

import java.util.UUID;

public record PermissionJobItem(
        @NotNull(message = "documentPublicId is required")
        UUID documentPublicId,
        @NotBlank(message = "driveFileId is required")
        String driveFileId,
        Long ownerUserId,
        @NotBlank(message = "targetUserEmail is required")
        @Email(message = "targetUserEmail must be valid")
        String targetUserEmail,
        @NotNull(message = "action is required")
        PermissionJobAction action,
        UUID familyId
) {
}
