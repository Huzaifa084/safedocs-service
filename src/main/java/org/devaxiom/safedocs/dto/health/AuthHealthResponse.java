package org.devaxiom.safedocs.dto.health;

public record AuthHealthResponse(
        Long userId,
        String email
) {
}
