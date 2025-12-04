package org.devaxiom.safedocs.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.exception.InvalidStateException;
import org.devaxiom.safedocs.exception.InvalidTokenException;
import org.devaxiom.safedocs.exception.TokenExpiredException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    private SecretKey signingKey;    // for JWS (HS512)

    @PostConstruct
    void init() {
        // Ensure both secrets are strong enough at startup
        validateSecretKey();
        // JWS signing key (HMAC-SHA512)
        this.signingKey = Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
        log.info("JwtService initialized (JWS signing only)");
    }

    // Encryption helpers removed (JWE no longer used)

    public String generateJwtToken() {
        return buildToken(JwtConfig.TOKEN_TYPE_ACCESS, jwtConfig.getAccessTtlMillis());
    }

    public String generatePasswordResetToken() {
        return buildToken(JwtConfig.TOKEN_TYPE_RESET, jwtConfig.getPasswordResetTtlMillis());
    }

    /**
     * Parse a token into a lightweight UserDetailsImpl (no authorities).
     * Useful when you just need identity token info (e.g., for debugging or simple flows),
     * not for building SecurityContext (where we always reload the User from DB).
     */
    public UserDetailsImpl parseToken(String token) {
        Claims claims = extractAllClaims(token);
        validateTokenClaims(claims);

        Long userId = getUserId(claims);
        String email = getEmail(claims);
        String username = getUsername(claims);
        Long tokenVer = getTokenVersion(claims);

        // For SecurityContext we always rebuild from DB; here we just expose basic info.
        return new UserDetailsImpl(
                userId,
                email,
                username,
                null,
                true,
                false,
                tokenVer
        );
    }

    public void validateJwtToken(String token) {
        // If you only care about "is it valid & not expired", this is enough.
        try {
            Claims claims = extractAllClaims(token);
            validateTokenExpiry(claims);
        } catch (ExpiredJwtException ex) {
            throw new TokenExpiredException("Token expired", ex);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid token", ex);
        }
    }

    /**
     * Validates token expiry and (optionally) required token type, then returns claims.
     */
    public Claims validate(String token, String expectedType) {
        try {
            Claims claims = extractAllClaims(token);
            validateTokenExpiry(claims);
            String type = claims.get(JwtConfig.TOKEN_TYPE, String.class);
            log.debug("Type of claims is {}", type);
            if (expectedType != null && !expectedType.equals(type)) {
                throw new InvalidTokenException("Invalid token type: " + type);
            }
            return claims;
        } catch (ExpiredJwtException ex) {
            throw new TokenExpiredException("Token expired", ex);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid token", ex);
        }
    }

    /**
     * Builds a signed JWT (JWS) for the currently authenticated user.
     */
    public String buildToken(String tokenType, long duration) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl principal)) {
            throw new InvalidStateException("No authenticated UserDetailsImpl found in SecurityContext");
        }

        Map<String, Object> claims = createClaims(principal, tokenType);
        Instant now = Instant.now();

        return Jwts.builder()
            .issuer(jwtConfig.getIssuer())
            .subject(principal.getEmail())
            .claims(claims)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(duration)))
            .compressWith(Jwts.ZIP.DEF)
            .signWith(signingKey, Jwts.SIG.HS512)
            .compact();
    }

    private Map<String, Object> createClaims(UserDetailsImpl user, String tokenType) {
        Map<String, Object> claims = new LinkedHashMap<>();

        claims.put(JwtConfig.CLAIM_USER_ID, user.getId());
        claims.put(JwtConfig.TOKEN_TYPE, tokenType);
        claims.put(JwtConfig.CLAIM_TOKEN_VER, user.getTokenVersion());
        claims.put("jti", UUID.randomUUID().toString());

        if (!user.getEmail().equals(user.getUsername())) {
            claims.put(JwtConfig.CLAIM_USERNAME, user.getUsername());
        }
        if (JwtConfig.TOKEN_TYPE_RESET.equals(tokenType)) {
            claims.put("nonce", UUID.randomUUID().toString());
        }

        return claims;
    }

    /**
     * Verifies a signed token and returns claims.
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private void validateSecretKey() {
        byte[] keyBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < jwtConfig.getMinSecretKeyLength()) {
            throw new InvalidStateException(
                    "JWT secret key must be at least 512 bits (64 characters). Current length: " +
                            (keyBytes.length * 8) + " bits"
            );
        }
    }

    // Encryption key validation removed (no JWE)

    private void validateTokenClaims(Claims claims) {
        if (!claims.containsKey(JwtConfig.CLAIM_USER_ID)) {
            throw new InvalidTokenException("Missing user ID claim");
        }
        if (!claims.containsKey(JwtConfig.CLAIM_TOKEN_VER)) {
            throw new InvalidTokenException("Missing token version");
        }
    }

    private void validateTokenExpiry(Claims claims) {
        if (claims.getExpiration().before(Date.from(Instant.now()))) {
            throw new TokenExpiredException("Token expired");
        }
    }

    public String getEmail(Claims claims) {
        return claims.getSubject();
    }

    public String getUsername(Claims claims) {
        return claims.getOrDefault(JwtConfig.CLAIM_USERNAME, claims.getSubject()).toString();
    }

    public Long getUserId(Claims claims) {
        return claims.get(JwtConfig.CLAIM_USER_ID, Long.class);
    }

    public Long getTokenVersion(Claims claims) {
        return claims.get(JwtConfig.CLAIM_TOKEN_VER, Long.class);
    }

    public String getTokenId(Claims claims) {
        return claims.get("jti", String.class);
    }

    public Long extractUserIdFromToken(String token) {
        try {
            Claims claims = extractAllClaims(token);
            return getUserId(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new InvalidTokenException("Invalid token", ex);
        }
    }
}
