package org.devaxiom.safedocs.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DevLoginRequest(
        @Schema(description = "User's email address for login", example = "sysowner@yopmail.com") @NotBlank @Email String email,
        @Schema(description = "User's password for login", example = "pass123") @NotBlank String secret) {
}
