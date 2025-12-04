package org.devaxiom.safedocs.dto.auth;

public record AuthResponse(
        String accessToken,
        long expiresInSeconds,
        UserProfileResponse user
) {
}
