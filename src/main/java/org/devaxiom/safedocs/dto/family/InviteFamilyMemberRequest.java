package org.devaxiom.safedocs.dto.family;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InviteFamilyMemberRequest(
        @NotBlank @Email String email
) {
}
