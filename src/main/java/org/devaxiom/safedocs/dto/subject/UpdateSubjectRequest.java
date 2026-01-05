package org.devaxiom.safedocs.dto.subject;

import jakarta.validation.constraints.NotBlank;

public record UpdateSubjectRequest(
        @NotBlank(message = "name is required")
        String name
) {
}
