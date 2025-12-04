package org.devaxiom.safedocs.dto.family;

import org.devaxiom.safedocs.enums.FamilyRole;

public record FamilyMemberResponse(
        Long userId,
        String email,
        String fullName,
        FamilyRole role,
        boolean active
) {
}
