package org.devaxiom.safedocs.dto.family;

import org.devaxiom.safedocs.enums.FamilyInviteStatus;

import java.util.UUID;

public record FamilyInviteResponse(
        UUID inviteId,
        UUID familyId,
        String familyName,
        String invitedEmail,
        String invitedByName,
        String invitedByEmail,
        FamilyInviteStatus status
) {
}
