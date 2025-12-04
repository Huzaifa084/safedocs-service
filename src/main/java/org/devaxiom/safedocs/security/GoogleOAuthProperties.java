package org.devaxiom.safedocs.security;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "auth.google")
public class GoogleOAuthProperties {

    /**
     * OAuth client ID for the SafeDocs mobile app.
     */
    @NotBlank
    private String clientId;

    /**
     * Optional additional client IDs (e.g., web client) that are allowed to issue ID tokens.
     */
    private List<String> additionalClientIds = new ArrayList<>();
}
