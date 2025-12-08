package org.devaxiom.safedocs.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.enums.AuthProviderType;
import org.devaxiom.safedocs.dto.family.CreateFamilyRequest;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.repository.UserRepository;
import org.devaxiom.safedocs.service.FamilyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Seeds a development user on application startup (idempotent).
 */
@Component
@Profile({"local", "dev"})
@RequiredArgsConstructor
@Slf4j
public class DevDataInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final FamilyService familyService;

    @Value("${dev.login.enabled:false}")
    private boolean devLoginEnabled;

    @Value("${dev.login.email:sysowner@yopmail.com}")
    private String devEmail;

    @Value("${dev.login.first-name:Dev}")
    private String devFirstName;

    @Value("${dev.login.last-name:User}")
    private String devLastName;

    @Override
    public void run(ApplicationArguments args) {
        if (!devLoginEnabled) {
            log.info("Dev login disabled; skipping dev user seeding");
            return;
        }
        String resolvedEmail = devEmail == null ? null : devEmail.trim().toLowerCase();
        Optional<User> existing = resolvedEmail == null ? Optional.empty() : userRepository.findByEmail(resolvedEmail);
        if (existing.isPresent()) {
            log.info("Dev user already exists: {}", resolvedEmail);
            return;
        }

        // Auto-correct a previously seeded placeholder email (e.g., starts with "${")
        Optional<User> placeholderUser = userRepository.findByEmail(devEmail)
                .filter(u -> u.getEmail() != null && u.getEmail().startsWith("${"));
        if (placeholderUser.isPresent() && resolvedEmail != null) {
            User u = placeholderUser.get();
            u.setEmail(resolvedEmail);
            u.setUsername(resolvedEmail);
            userRepository.save(u);
            tryCreateDefaultFamily(u);
            log.info("Corrected placeholder dev user email to {}", resolvedEmail);
            return;
        }
        User user = User.builder()
                .publicId(UUID.randomUUID())
                .email(resolvedEmail)
                .username(resolvedEmail)
                .firstName(devFirstName)
                .lastName(devLastName)
                .passwordHash("DEV_LOCAL")
                .isActive(true)
                .deleted(false)
                .tokenVersion(0L)
                .authProviderType(AuthProviderType.LOCAL)
                .build();
        user = userRepository.save(user);
        tryCreateDefaultFamily(user);
        log.info("Seeded dev user {}", user.getEmail());
    }

    private void tryCreateDefaultFamily(User user) {
        try {
            familyService.createFamily(user, new CreateFamilyRequest("Family of " + user.getFullName()));
        } catch (Exception ignored) {
        }
    }
}
