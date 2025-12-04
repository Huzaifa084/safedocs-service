package org.devaxiom.safedocs.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.exception.InvalidTokenException;
import org.devaxiom.safedocs.exception.UnauthorizedException;
import org.devaxiom.safedocs.exception.UserNotFoundException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.security.JwtConfig;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationProvider {
    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public void authenticateToken(String jwtToken, HttpServletRequest request) {
        var claims = jwtService.validate(jwtToken, JwtConfig.TOKEN_TYPE_ACCESS);
        Long userId = jwtService.getUserId(claims);
        Long tokenV = jwtService.getTokenVersion(claims);


        User user = userDetailsService.getUserById(userId);
        if (user == null) throw new UserNotFoundException("User not found");
        if (Boolean.FALSE.equals(user.getIsActive())) throw new UnauthorizedException("User account is disabled");
        if (!tokenV.equals(user.getTokenVersion())) {
            // Explicit logging to aid debugging post-logout token usage
            throw new InvalidTokenException("Token has been revoked (tokenVersion=" + tokenV + ", current=" + user.getTokenVersion() + ")");
        }

        Authentication existing = SecurityContextHolder.getContext().getAuthentication();
        if (existing != null && existing.getPrincipal() instanceof UserDetailsImpl) return;

        UserDetailsImpl details = UserDetailsImpl.from(user);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());

        if (request != null) {
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        }
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
