package org.devaxiom.safedocs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.auth.AuthResponse;
import org.devaxiom.safedocs.dto.auth.DevLoginRequest;
import org.devaxiom.safedocs.dto.auth.GoogleLoginRequest;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${dev.login.enabled:false}")
    private boolean devLoginEnabled;

    @Value("${dev.login.secret:}")
    private String devLoginSecret;

    @PostMapping("/google")
    public BaseResponseEntity<AuthResponse> loginWithGoogle(
            @Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.loginWithGoogle(request.idToken());
        return ResponseBuilder.success(response, "Login successful");
    }

    @PostMapping("/dev")
    public BaseResponseEntity<AuthResponse> devLogin(
            @Valid @RequestBody DevLoginRequest request) {
        AuthResponse response = authService.devLogin(request.email(), request.secret(), devLoginEnabled, devLoginSecret);
        return ResponseBuilder.success(response, "Dev login successful");
    }
}
