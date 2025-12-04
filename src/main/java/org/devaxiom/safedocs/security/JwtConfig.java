package org.devaxiom.safedocs.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtConfig {

    // TODO: Decrease the time to 30 minutes for production
//    private Duration accessTtl = Duration.ofMinutes(30);
    private Duration accessTtl = Duration.ofDays(30);
    private Duration preAuthTtl = Duration.ofMinutes(5);
    private Duration passwordResetTtl = Duration.ofMinutes(15);
    private String secret;
    private String encryptionSecret;
    private String issuer = "wiser-helpdesk";
    private int minSecretKeyLength = 64;
    public static final String TOKEN_TYPE_ACCESS = "ACCESS";
    public static final String TOKEN_TYPE_PRE_AUTH = "PRE_AUTH";
    public static final String TOKEN_TYPE_RESET = "PASSWORD_RESET";

    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_USER_ID = "userId";
    public static final String CLAIM_USERNAME = "username";
    public static final String CLAIM_TOKEN_VER = "tokenVer";
    public static final String TOKEN_TYPE = "tokenType";

    public long getAccessTtlMillis() {
        return accessTtl.toMillis();
    }

    public long getPreAuthTtlMillis() {
        return preAuthTtl.toMillis();
    }

    public long getPasswordResetTtlMillis() {
        return passwordResetTtl.toMillis();
    }
}
