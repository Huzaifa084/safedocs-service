package org.devaxiom.safedocs.storage;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

@Data
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {
    private long maxBytes = 5242880;
    private Set<String> allowedContentTypes;
    private int retentionDays = 90;
    private int tmpRetentionHours = 24;
}
