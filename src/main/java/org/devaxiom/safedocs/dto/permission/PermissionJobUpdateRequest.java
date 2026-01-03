package org.devaxiom.safedocs.dto.permission;

import jakarta.validation.constraints.NotNull;
import org.devaxiom.safedocs.enums.PermissionJobStatus;

public record PermissionJobUpdateRequest(
        @NotNull(message = "status is required")
        PermissionJobStatus status,
        Integer attempts,
        String lastError
) {
}
