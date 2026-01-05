package org.devaxiom.safedocs.dto.family;

import jakarta.validation.constraints.NotNull;
import org.devaxiom.safedocs.enums.FamilyRole;

public record UpdateFamilyMemberRoleRequest(
        @NotNull FamilyRole role
) {
}
