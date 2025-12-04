package org.devaxiom.safedocs.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Arrays;
import java.util.Set;

@Configuration
@Slf4j
@RequiredArgsConstructor
@EnableConfigurationProperties(S3Props.class)
public class S3Config {

    private static final Set<String> LOCAL_PROFILES = Set.of("dev", "local");
    private static final String DEFAULT_MINIO_ENDPOINT = "http://minio:9000";

    private final Environment environment;

    @Bean
    S3Client s3Client(S3Props props) {
        var cfg = S3Configuration.builder()
                .pathStyleAccessEnabled(props.isPathStyle())
                .build();

        var builder = S3Client.builder()
                .region(Region.of(props.getRegion()))
                .serviceConfiguration(cfg)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));

        String endpoint = resolveEndpoint(props);
        if (StringUtils.hasText(endpoint)) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }

        log.info("Configuring S3 client -> bucket='{}', region='{}', pathStyle={}, endpoint={}",
                props.getBucket(),
                props.getRegion(),
                props.isPathStyle(),
                endpoint != null ? endpoint : "<AWS-managed>");
        return builder.build();
    }

    @Bean
    S3Presigner s3Presigner(S3Props props) {
        var cfg = S3Configuration.builder()
                .pathStyleAccessEnabled(props.isPathStyle())
                .build();

        var builder = S3Presigner.builder()
                .region(Region.of(props.getRegion()))
                .serviceConfiguration(cfg)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())));

        String endpoint = resolvePresignerEndpoint(props);
        if (StringUtils.hasText(endpoint)) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    private String resolvePresignerEndpoint(S3Props props) {
        if (StringUtils.hasText(props.getPresignerEndpoint())) {
            return props.getPresignerEndpoint();
        }
        return resolveEndpoint(props);
    }

    private String resolveEndpoint(S3Props props) {
        if (StringUtils.hasText(props.getEndpoint())) {
            return props.getEndpoint();
        }
        String[] profiles = environment.getActiveProfiles();
        boolean isLocalProfile = Arrays.stream(profiles).anyMatch(LOCAL_PROFILES::contains);
        if (isLocalProfile) {
            log.warn("storage.s3.endpoint not configured for profiles {}; falling back to {}",
                    String.join(", ", profiles),
                    DEFAULT_MINIO_ENDPOINT);
            return DEFAULT_MINIO_ENDPOINT;
        }
        return null;
    }
}
