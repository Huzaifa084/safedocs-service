package org.devaxiom.safedocs.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.security.UserDetailsImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class PrincipleUserService {

    @PersistenceContext
    private EntityManager entityManager;

    public Optional<User> getCurrentUser() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            log.debug("getCurrentUser called but no authenticated user found");
            return Optional.empty();
        }
        try {
            User ref = entityManager.getReference(User.class, userId);
            return Optional.of(ref);
        } catch (Exception e) {
            log.warn("Failed to load current user with ID {}: {}", userId, e.getMessage());
            return Optional.empty();
        }
    }

    public Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            log.debug("No authenticated user found for auditing");
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetailsImpl userDetails) {
            log.debug("Found logged user for userId: {}, username: {}", userDetails.getId(), userDetails.getUsername());
            return userDetails.getId();
        }
        return null;
    }

}
