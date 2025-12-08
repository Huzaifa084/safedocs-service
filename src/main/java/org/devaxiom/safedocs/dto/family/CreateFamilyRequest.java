package org.devaxiom.safedocs.dto.family;

import jakarta.validation.constraints.NotBlank;

public record CreateFamilyRequest(
        @NotBlank String name
) {
}

