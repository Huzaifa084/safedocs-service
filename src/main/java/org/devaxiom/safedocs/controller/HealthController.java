package org.devaxiom.safedocs.controller;

import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.dto.health.AuthHealthResponse;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
public class HealthController {

    private final PrincipleUserService principleUserService;

    @GetMapping("/auth")
    public BaseResponseEntity<AuthHealthResponse> auth() {
        User user = principleUserService.getCurrentUser()
                .orElseThrow(() -> new BadRequestException("Unauthorized"));
        AuthHealthResponse response = new AuthHealthResponse(user.getId(), user.getEmail());
        return ResponseBuilder.success(response, "Auth OK");
    }
}
