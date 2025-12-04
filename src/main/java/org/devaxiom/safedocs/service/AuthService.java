package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.dto.auth.AuthResponse;
import org.devaxiom.safedocs.dto.auth.UserProfileResponse;
import org.devaxiom.safedocs.enums.AuthProviderType;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.exception.UnauthorizedException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.repository.UserRepository;
import org.devaxiom.safedocs.security.GoogleTokenVerifier;
import org.devaxiom.safedocs.security.JwtConfig;
import org.devaxiom.safedocs.security.JwtService;
import org.devaxiom.safedocs.security.UserDetailsImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final JwtConfig jwtConfig;
    private final GoogleTokenVerifier googleTokenVerifier;

    @Transactional
    public AuthResponse loginWithGoogle(String idToken) {
        GoogleTokenVerifier.GoogleProfile profile = googleTokenVerifier.verify(idToken);

        User user = userRepository.findByEmail(profile.email())
                .map(existing -> updateExistingUser(existing, profile))
                .orElseGet(() -> createUser(profile));

        if (Boolean.FALSE.equals(user.getIsActive()) || Boolean.TRUE.equals(user.getDeleted())) {
            throw new UnauthorizedException("User account is disabled");
        }

        return issueTokens(user);
    }

    private User updateExistingUser(User user, GoogleTokenVerifier.GoogleProfile profile) {
        boolean dirty = false;
        if (user.getAuthProviderType() != null && user.getAuthProviderType() != AuthProviderType.GOOGLE) {
            throw new BadRequestException("Account is registered with a different provider");
        }
        if (user.getProviderId() != null && profile.providerId() != null
                && !user.getProviderId().equals(profile.providerId())) {
            throw new BadRequestException("Account is linked to a different Google identity");
        }
        if (user.getAuthProviderType() == null) {
            user.setAuthProviderType(AuthProviderType.GOOGLE);
            dirty = true;
        }
        if (user.getProviderId() == null) {
            user.setProviderId(profile.providerId());
            dirty = true;
        }
        if (user.getUsername() == null || user.getUsername().isBlank()) {
            user.setUsername(profile.email());
            dirty = true;
        }
        if (user.getFirstName() == null || user.getFirstName().isBlank()) {
            user.setFirstName(Optional.ofNullable(profile.givenName()).orElse(""));
            dirty = true;
        }
        if (user.getLastName() == null || user.getLastName().isBlank()) {
            user.setLastName(Optional.ofNullable(profile.familyName()).orElse(""));
            dirty = true;
        }
        if (dirty) {
            user = userRepository.save(user);
        }
        return user;
    }

    private User createUser(GoogleTokenVerifier.GoogleProfile profile) {
        String first = Optional.ofNullable(profile.givenName()).orElse("");
        String last = Optional.ofNullable(profile.familyName()).orElse("");

        User user = User.builder()
                .publicId(UUID.randomUUID())
                .email(profile.email())
                .username(profile.email())
                .firstName(first)
                .lastName(last)
                .passwordHash("OAUTH2_GOOGLE")
                .isActive(true)
                .deleted(false)
                .tokenVersion(0L)
                .authProviderType(AuthProviderType.GOOGLE)
                .providerId(profile.providerId())
                .build();
        user = userRepository.save(user);
        log.info("Created new Google user {}", user.getEmail());
        return user;
    }

    private AuthResponse issueTokens(User user) {
        UserDetailsImpl details = UserDetailsImpl.from(user);
        var auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        String access = jwtService.buildToken(JwtConfig.TOKEN_TYPE_ACCESS, jwtConfig.getAccessTtlMillis());
        long expiresInSeconds = jwtConfig.getAccessTtlMillis() / 1000;

        return new AuthResponse(
                access,
                expiresInSeconds,
                new UserProfileResponse(
                        user.getId(),
                        user.getPublicId(),
                        user.getEmail(),
                        user.getFirstName(),
                        user.getLastName(),
                        user.getFullName()
                )
        );
    }
}
