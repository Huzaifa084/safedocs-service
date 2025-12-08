package org.devaxiom.safedocs.dto.family;

import org.devaxiom.safedocs.enums.FamilyRole;

import java.util.UUID;

public record FamilySummaryResponse(
        UUID familyId,
        String familyName,
        FamilyRole role,
        int memberCount
) {
}

