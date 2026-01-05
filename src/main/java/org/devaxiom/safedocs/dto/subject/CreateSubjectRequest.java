package org.devaxiom.safedocs.dto.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.devaxiom.safedocs.enums.SubjectScope;

import java.util.UUID;

public record CreateSubjectRequest(
        @NotBlank(message = "name is required")
        String name,
        String semesterLabel,
        @NotNull(message = "scope is required")
        SubjectScope scope,
        UUID familyId
) {
}
