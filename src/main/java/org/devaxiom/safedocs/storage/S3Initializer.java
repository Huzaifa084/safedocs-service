package org.devaxiom.safedocs.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class S3Initializer {

    private final S3Client s3Client;
    private final S3Props s3Props;

    @EventListener(ApplicationReadyEvent.class)
    public void createBucketIfNotExists() {
        if (!s3Props.isAutoCreateBucket()) {
            log.info("S3 auto-create bucket disabled; skipping bucket initialization");
            return;
        }
        if (!StringUtils.hasText(s3Props.getBucket())) {
            log.warn("storage.s3.bucket is empty; skipping bucket initialization");
            return;
        }
        try {
            s3Client.headBucket(HeadBucketRequest.builder()
                    .bucket(s3Props.getBucket())
                    .build());
            log.info("Bucket '{}' exists", s3Props.getBucket());
        } catch (NoSuchBucketException e) {
            log.info("Bucket '{}' does not exist, creating it...", s3Props.getBucket());
            CreateBucketRequest.Builder req = CreateBucketRequest.builder().bucket(s3Props.getBucket());
            // For AWS S3 regions other than us-east-1, set location constraint
            if (!"us-east-1".equalsIgnoreCase(s3Props.getRegion())) {
                req = req.createBucketConfiguration(CreateBucketConfiguration.builder()
                        .locationConstraint(s3Props.getRegion())
                        .build());
            }
            s3Client.createBucket(req.build());
            log.info("Bucket '{}' created successfully", s3Props.getBucket());
        } catch (S3Exception sx) {
            // If bucket already exists or is owned by you, just log and continue
            String code = sx.awsErrorDetails() != null ? sx.awsErrorDetails().errorCode() : "";
            if ("BucketAlreadyOwnedByYou".equals(code) || "BucketAlreadyExists".equals(code)) {
                log.info("Bucket '{}' already present: {}", s3Props.getBucket(), code);
            } else if (sx.statusCode() == 520) {
                log.error("S3 endpoint connectivity issue (520). Check proxy configuration for: {}",
                        s3Props.getEndpoint());
            } else {
                log.warn("S3 bucket check/create failed: {}", sx.getMessage());
            }
        }
    }
}
