package org.devaxiom.safedocs.security;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PublicEndpoints {
    public static final List<String> PUBLIC_ENDPOINTS = List.of(

            // --- AUTH ---
            "/api/auth/dev",
            "/api/auth/google",
            "/api/auth/otp/**",
            "/api/auth/reset/**",
            "/api/auth/invite/complete",
            "/api/auth/password/forgot",
            "/api/auth/password/verify",
            "/api/auth/password/reset",

            // --- OPEN DOCS ---
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/webjars/**",

            // --- PUBLIC APIs ---
            "/api/public/**",
            "/api/health",

            // --- DEV DEBUG ---
            "/api/debug/**",
            "/dev/**",

            // --- STATIC FILES ---
            "/favicon.ico"
    );
}
