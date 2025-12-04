package org.devaxiom.safedocs.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.InputStream;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.devaxiom.safedocs.storage.MediaKeys.safeFileName;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorageService {

    private final S3Client s3;
    private final S3Props s3props;
    private final StorageProperties props;
    private final S3Presigner presigner;
    private final Tika tika = new Tika();


    public UploadResult uploadFile(
            StorageContext context, String identifier,
            MultipartFile file, User principal,
            Set<String> allowedMimeTypes) {

        log.info("===> Uploading file");
        if (file == null || file.isEmpty()) throw new BadRequestException("Empty file");

        long size = file.getSize();
        long max = props.getMaxBytes() > 0 ? props.getMaxBytes() : 5 * 1024 * 1024;
        if (size <= 0 || size > max) throw new BadRequestException("File too large");
        log.info("===> File with size {} uploaded successfully", size);

        String origName = Optional.ofNullable(file.getOriginalFilename()).orElse("file");
        String safeName = safeFileName(origName);
        log.info("===> File with safe name {} uploaded successfully", safeName);

        String detected = detectMime(file);
        if (allowedMimeTypes != null && !allowedMimeTypes.isEmpty()) {
            log.info("===> Validating MIME type: {}", detected);
            if (detected == null || !allowedMimeTypes.contains(detected)) {
                log.info("===> Unsupported content type: {}", detected);
                throw new BadRequestException("Unsupported content type: " + detected);
            }
        }
        log.info("===> File with size {} uploaded successfully", size);

        String key;
        if (identifier != null) {
            try {
                log.info("===> Uploading file with identifier {}", identifier);
                key = MediaKeys.finalKey(context, UUID.fromString(identifier), safeName);
                log.info("===> File with identifier {} uploaded successfully", key);
            } catch (IllegalArgumentException iae) {
                throw new BadRequestException("Invalid identifier");
            }
        } else {
            log.info("===> Uploading file without identifier");
            key = MediaKeys.finalKey(context, safeName);
        }

        try (InputStream in = file.getInputStream()) {
            log.info("===> Uploading file {} uploaded successfully", key);
            var put = PutObjectRequest.builder()
                    .bucket(s3props.getBucket())
                    .key(key)
                    .contentType(detected)
                    .contentLength(size)
                    .metadata(Map.of(
                            "context", context.getPrefix(),
                            "uploadedBy", principal.getFirstName() + " " + principal.getLastName(),
                            "originalFilename", safeName
                    ))
                    .build();

            PutObjectResponse resp = s3.putObject(put, RequestBody.fromInputStream(in, size));
            log.info("===> Uploading file {} uploaded successfully", key);
            SdkHttpResponse http = resp.sdkHttpResponse();
            if (http == null || !http.isSuccessful()) {
                log.info("===> Uploading file {} uploaded successfully", key);
                throw new BadRequestException("Upload failed");
            }
            log.info("===> Uploading file {} uploaded successfully", key);
            return new UploadResult(key, safeName, detected, size);
        } catch (S3Exception e) {
            if (e.awsErrorDetails() != null && "NoSuchBucket".equals(e.awsErrorDetails().errorCode())) {
                throw new BadRequestException("Storage bucket not configured properly");
            }
            throw new BadRequestException("Upload failed: " + e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            throw new BadRequestException("Upload failed: " + e.getMessage());
        }
    }


    private String detectMime(MultipartFile f) {
        try {
            return tika.detect(f.getInputStream(), f.getOriginalFilename());
        } catch (Exception ignore) {
            return f.getContentType();
        }
    }

    /* ========= Presign (temporary public URL) ========= */
    public String presignGetUrl(String key) {
        if (key == null || key.isBlank()) {
            throw new BadRequestException("Missing key");
        }
        var get = GetObjectRequest.builder()
                .bucket(s3props.getBucket())
                .key(key)
                .build();
        var presignReq = GetObjectPresignRequest.builder()
                .signatureDuration(java.time.Duration.ofSeconds(s3props.getPresignExpSeconds()))
                .getObjectRequest(get)
                .build();
        return presigner.presignGetObject(presignReq).url().toString();
    }

    /* ========= Retention (delete > 90d; tmp > 24h) ========= */
    // Runs nightly at 03:15 server time.
    @Scheduled(cron = "0 15 3 * * *")
    public void retentionSweep() {
        log.info("===> Starting retention sweep");
        Instant cutoff = Instant.now().minusSeconds(props.getRetentionDays() * 24L * 3600L);
        deleteOlderThan("tickets/", cutoff);

        Instant tmpCutoff = Instant.now().minusSeconds(props.getTmpRetentionHours() * 3600L);
        deleteOlderThan("temp/", tmpCutoff);
    }

    private void deleteOlderThan(String prefix, Instant cutoff) {
        String token = null;
        do {
            var list = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(s3props.getBucket())
                    .prefix(prefix)
                    .continuationToken(token)
                    .build());
            list.contents().forEach(o -> {
                Instant lastMod = o.lastModified() != null
                        ? o.lastModified().atOffset(ZoneOffset.UTC).toInstant()
                        : Instant.EPOCH;
                if (lastMod.isBefore(cutoff)) {
                    try {
                        s3.deleteObject(DeleteObjectRequest.builder()
                                .bucket(s3props.getBucket()).key(o.key()).build());
                    } catch (Exception ignored) {
                    }
                }
            });
            token = list.nextContinuationToken();
        } while (token != null && !token.isBlank());
    }

    /* ========= DTO ========= */
    public record UploadResult(String key, String filename, String mimeType, long size) {
    }
}
