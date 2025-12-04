package org.devaxiom.safedocs.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleTokenVerifier {

    private final GoogleOAuthProperties properties;

    public GoogleProfile verify(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new BadRequestException("Google ID token is required");
        }
        GoogleIdTokenVerifier verifier = buildVerifier();
        try {
            GoogleIdToken token = verifier.verify(idToken);
            if (token == null) {
                throw new BadRequestException("Invalid Google ID token");
            }
            Payload payload = token.getPayload();
            String email = payload.getEmail();
            if (email == null || email.isBlank()) {
                throw new BadRequestException("Google account email is missing");
            }
            boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
            if (!emailVerified) {
                throw new BadRequestException("Google account email is not verified");
            }
            return new GoogleProfile(
                    payload.getSubject(),
                    email.trim().toLowerCase(),
                    string(payload, "given_name"),
                    string(payload, "family_name"),
                    string(payload, "name"),
                    string(payload, "picture"),
                    emailVerified
            );
        } catch (GeneralSecurityException | java.io.IOException ex) {
            log.warn("Failed to verify Google ID token: {}", ex.getMessage());
            throw new BadRequestException("Unable to verify Google ID token");
        }
    }

    private GoogleIdTokenVerifier buildVerifier() {
        List<String> audiences = new ArrayList<>();
        if (properties.getClientId() != null && !properties.getClientId().isBlank()) {
            audiences.add(properties.getClientId());
        }
        if (!CollectionUtils.isEmpty(properties.getAdditionalClientIds())) {
            properties.getAdditionalClientIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(audiences::add);
        }
        if (audiences.isEmpty()) {
            throw new BadRequestException("Google OAuth client ID is not configured");
        }
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(audiences)
                .build();
    }

    private String string(Payload payload, String key) {
        Object v = payload.get(key);
        return v != null ? v.toString() : null;
    }

    public record GoogleProfile(
            String providerId,
            String email,
            String givenName,
            String familyName,
            String fullName,
            String pictureUrl,
            boolean emailVerified
    ) {
    }
}
