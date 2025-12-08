package org.devaxiom.safedocs.dto.family;

import org.devaxiom.safedocs.enums.FamilyRole;

import java.util.List;
import java.util.UUID;

public record FamilyProfileResponse(
        UUID familyId,
        String familyName,
        UUID headUserId,
        String headName,
        List<FamilyMemberResponse> members
) {
}

