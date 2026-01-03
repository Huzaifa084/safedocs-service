package org.devaxiom.safedocs.dto.permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PermissionJobCreateRequest(
        @NotEmpty(message = "jobs are required")
        List<@Valid PermissionJobItem> jobs
) {
}
