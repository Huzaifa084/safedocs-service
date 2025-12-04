package org.devaxiom.safedocs.storage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.security.UserDetailsImpl;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/storage")
@Tag(name = "Media Storage Management", description = "APIs for managing media storage")
@Slf4j
@RequiredArgsConstructor
public class StorageController {
    private final StorageService storageService;
    private final PrincipleUserService principleUserService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload profile image", description = "Upload a profile image for the authenticated user")
    public ResponseEntity<StorageService.UploadResult> uploadProfileImage(
            @RequestParam("file") MultipartFile file) {

        Optional<User> session = principleUserService.getCurrentUser();
        User user = session.orElseThrow(() -> new BadRequestException("Unauthorized"));
        ProfileScope scope = resolveProfileScope(user);
        log.info("Uploading profile image for {}", scope.ownerLabel());

        StorageService.UploadResult result = storageService.uploadFile(
                StorageContext.PROFILES, scope.ownerId(), file, scope, PROFILE_ALLOWED_TYPES);
        log.info("Profile image uploaded successfully for {}", scope.ownerLabel());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/presign")
    @Operation(summary = "Generate presigned URL for accessing a stored object",
            description = "Generates a presigned URL for accessing a stored object. " +
                    "The 'key' parameter must start with 'profiles/{userPublicId}/' for profile images.")
    public ResponseEntity<Map<String, String>> presign(
            @RequestParam("key") String key) {

        Optional<User> session = principleUserService.getCurrentUser();
        User user = session.orElseThrow(() -> new BadRequestException("Unauthorized"));
        ProfileScope scope = resolveProfileScope(user);
        if (!key.startsWith(scope.keyPrefix())) throw new BadRequestException("Not allowed to access this key");

        String url = storageService.presignGetUrl(key);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @PostMapping(value = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StorageService.UploadResult> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "entityId", required = false) String entityId,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        StorageService.UploadResult result = storageService.uploadFile(
                StorageContext.DOCUMENTS, entityId, file, principal, DOCUMENT_ALLOWED_TYPES);
        return ResponseEntity.ok(result);
    }

    private ProfileScope resolveProfileScope(User user) {
        if (user == null || user.getPublicId() == null) throw new BadRequestException("User profile unavailable");
        return new ProfileScope(user.getPublicId().toString(), profilePrefix(user.getPublicId()), "user:%s".formatted(user.getPublicId()));
    }

    private String profilePrefix(java.util.UUID ownerPid) {
        return StorageContext.PROFILES.getPrefix() + "/" + ownerPid + "/";
    }

    private record ProfileScope(String ownerId, String keyPrefix, String ownerLabel) {
    }

    private static final Set<String> PROFILE_ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp"
    );

    private static final Set<String> DOCUMENT_ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png",
            "application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

//    @GetMapping("/download/{key:.+}")
//    public ResponseEntity<InputStreamResource> downloadFile(
//            @PathVariable String key,
//            @AuthenticationPrincipal Object principal) {
//        // You might want to add context-based authorization here
//        return storageService.streamFile(key, principal);
//    }

    // Add a new method in MediaStorageService for generic file streaming
    // @Transactional(readOnly = true)
    // public ResponseEntity<InputStreamResource> streamFile(String key, Object principal) {
    //     // Similar to streamTicketObject but with different authorization logic
    // }
}
