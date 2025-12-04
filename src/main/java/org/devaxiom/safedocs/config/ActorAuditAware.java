package org.devaxiom.safedocs.config;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@EnableJpaAuditing
@RequiredArgsConstructor
@Slf4j
public class ActorAuditAware implements AuditorAware<User> {

    private final PrincipleUserService principleUserService;
    private final EntityManager em;

    @Override
    @NonNull
    public Optional<User> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            log.info("[Audit] no authenticated user in SecurityContext");
            return Optional.empty();
        }

        try {
            Optional<User> currentUser = principleUserService.getCurrentUser();
            if (currentUser.isEmpty()) {
                log.info("[Audit] could not resolve current user");
                return Optional.empty();
            }

            Long effectiveUserId = currentUser.map(User::getUserId).orElse(null);
            if (effectiveUserId == null) {
                log.info("[Audit] could not resolve effective user id");
                return Optional.empty();
            }

            return Optional.of(em.getReference(User.class, effectiveUserId));

        } catch (AuthenticationException ex) {
            log.info("[Audit] No authenticated user found for auditing: {}", ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            log.info("[Audit] failed to resolve current auditor: {}", ex.getMessage());
            return Optional.empty();
        }
    }

}