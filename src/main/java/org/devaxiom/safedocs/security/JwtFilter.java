package org.devaxiom.safedocs.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.exception.InvalidTokenException;
import org.devaxiom.safedocs.exception.TokenExpiredException;
import org.devaxiom.safedocs.exception.UnauthorizedException;
import org.devaxiom.safedocs.exception.UserNotFoundException;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.devaxiom.safedocs.security.PublicEndpoints.PUBLIC_ENDPOINTS;

@Component
@AllArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {
    private final AuthEntryPoint authEntryPoint;
    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    private final JwtService jwtService; // kept for future token operations
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        log.info("➡️ {}", request.getRequestURI());

        String token = parseJwt(request);

        if (isPublicEndpoint(request.getRequestURI())) {
            log.info("JwtFilter: Open endpoint, skipping authentication for {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        } else if (token == null) {
            log.warn("JwtFilter: No JWT token found in request headers for {}", request.getRequestURI());
            authEntryPoint.handleJwtException(response, new UnauthorizedException("No JWT token provided"));
            return;

        }

        String authorizationHeader = this.parseJwt(request);
        try {
            jwtAuthenticationProvider.authenticateToken(authorizationHeader, request);
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            // Snapshot authorities to avoid ConcurrentModificationException if underlying collection mutates during logging
            List<?> authoritySnapshot = auth.getAuthorities() != null ? new ArrayList<>(auth.getAuthorities()) : List.of();
            log.info("Spring Security Authorities for '{}': {}", auth.getName(), authoritySnapshot);

            log.info("JwtFilter: Authentication set in SecurityContext for user: {}", auth.getName());
        } catch (JwtException | UnauthorizedException | TokenExpiredException | UserNotFoundException |
                 InvalidTokenException ex) {
            log.warn("Authentication failed: {}", ex.getMessage());
            authEntryPoint.handleJwtException(response, ex);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String uri) {
        return PUBLIC_ENDPOINTS.stream()
                .anyMatch(pattern -> PATH_MATCHER.match(pattern, uri));
    }


    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer "))
            return headerAuth.substring(7);
        return null;
    }
}
