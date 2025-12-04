package org.devaxiom.safedocs.dto.auth;

import java.util.UUID;

public record UserProfileResponse(
        Long id,
        UUID publicId,
        String email,
        String firstName,
        String lastName,
        String fullName
) {
}
