package org.devaxiom.safedocs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.BaseResponse;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.dto.document.AddShareRequest;
import org.devaxiom.safedocs.dto.document.CreateDocumentRequest;
import org.devaxiom.safedocs.dto.document.DocumentResponse;
import org.devaxiom.safedocs.dto.document.DocumentShareResponse;
import org.devaxiom.safedocs.dto.document.DocumentPageResponse;
import org.devaxiom.safedocs.dto.document.DocumentListItem;
import org.devaxiom.safedocs.dto.document.DocumentReconcileRequest;
import org.devaxiom.safedocs.dto.document.DocumentReconcileResponse;
import org.devaxiom.safedocs.dto.document.UpdateDocumentRequest;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.DocumentService;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final PrincipleUserService principleUserService;

        @PostMapping
        public BaseResponseEntity<DocumentResponse> createOrUpsertDocument(
            @Valid @RequestBody CreateDocumentRequest request
        ) {
        User user = principleUserService.getCurrentUser().orElseThrow(()
            -> new BadRequestException("Unauthorized"));
        DocumentResponse resp = documentService.upsertDocument(request, user);
        return ResponseBuilder.success(resp, "Document saved");
        }

    @PutMapping(value = "/{id}")
    public BaseResponseEntity<DocumentResponse> updateDocument(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateDocumentRequest request
    ) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        DocumentResponse resp = documentService.updateDocument(parseId(id), request, user);
        return ResponseBuilder.success(resp, "Document updated");
    }

    @GetMapping
    public BaseResponseEntity<DocumentPageResponse> list(
            @Parameter(
                    description = "Visibility filter",
                    schema = @Schema(implementation = DocumentVisibility.class),
                    example = "SHARED"
            )
            @RequestParam("visibility") DocumentVisibility visibility,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "familyId", required = false) String familyId) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        if (type != null) {
            throw new BadRequestException("Use visibility= instead of type=");
        }
        DocumentService.DocumentFilter filter = new DocumentService.DocumentFilter(
            visibility,
                category,
                search,
                page,
                size,
                familyId != null ? parseId(familyId) : null
        );
        DocumentPageResponse resp = documentService.listWithFilters(filter, user);
        return ResponseBuilder.success(resp, "Documents fetched");
    }

    @GetMapping("/shared/with-me")
    public BaseResponseEntity<DocumentPageResponse> sharedWithMe(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        DocumentPageResponse items = documentService.listSharedWith(user, page, size, search);
        return ResponseBuilder.success(items, "Shared documents fetched");
    }

    @GetMapping("/{id}")
    public BaseResponseEntity<DocumentResponse> getDocument(@PathVariable("id") String id) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        DocumentResponse resp = documentService.getDocument(parseId(id), user);
        return ResponseBuilder.success(resp, "Document fetched");
    }

    @DeleteMapping("/{id}")
    public BaseResponseEntity<?> deleteDocument(@PathVariable("id") String id) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        documentService.deleteDocument(parseId(id), user);
        return ResponseBuilder.success("Document deleted");
    }

    @PostMapping("/reconcile")
    public BaseResponseEntity<DocumentReconcileResponse> reconcile(
            @Valid @RequestBody DocumentReconcileRequest request
    ) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        DocumentReconcileResponse resp = documentService.reconcile(request, user);
        return ResponseBuilder.success(resp, "Reconciled");
    }

    @PostMapping("/{id}/share")
    public BaseResponseEntity<List<DocumentShareResponse>> addShare(
            @PathVariable("id") String id,
            @Valid @RequestBody AddShareRequest request) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        List<DocumentShareResponse> responses = documentService.addShares(parseId(id), request.emails(), user);
        return ResponseBuilder.success(responses, "Share entries added");
    }

    @DeleteMapping("/{id}/share/{shareId}")
    public BaseResponseEntity<?> removeShare(
            @PathVariable("id") String id,
            @PathVariable("shareId") Long shareId) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        documentService.removeShare(parseId(id), shareId, user);
        return ResponseBuilder.success("Share entry removed");
    }

    @GetMapping("/{id}/share")
    public BaseResponseEntity<List<DocumentShareResponse>> listShares(
            @PathVariable("id") String id) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        List<DocumentShareResponse> shares = documentService.listShares(parseId(id), user);
        return ResponseBuilder.success(shares, "Share recipients fetched");
    }

    @GetMapping("/{id}/download")
    public BaseResponseEntity<Void> download(@PathVariable("id") String id) {
        BaseResponse<Void> body = BaseResponse.<Void>builder()
                .success(false)
                .message("Stored in Google Drive. Use Drive API.")
                .build();
        return new BaseResponseEntity<>(body, HttpStatus.GONE);
    }

    // Enum request parameters for visibility are handled by Spring automatically

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid document id");
        }
    }
}
