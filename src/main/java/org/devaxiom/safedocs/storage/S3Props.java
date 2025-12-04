package org.devaxiom.safedocs.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "storage.s3")
public class S3Props {
    @NotBlank
    private String bucket;
    @NotBlank
    private String region = "us-east-1";
    private String endpoint;
    private String presignerEndpoint;
    private boolean pathStyle = true;
    @NotBlank
    private String accessKey;
    @NotBlank
    private String secretKey;
    @Positive
    private int presignExpSeconds = 900;
    private boolean autoCreateBucket = false;
}
