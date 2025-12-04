package org.devaxiom.safedocs.dto.auth;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        long expiresInSeconds,
        UserProfileResponse user
) {
}
