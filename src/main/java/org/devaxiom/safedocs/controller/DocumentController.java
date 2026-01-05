package org.devaxiom.safedocs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.base.BaseResponse;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.dto.document.AddShareRequest;
import org.devaxiom.safedocs.dto.document.BulkDeleteDocumentsRequest;
import org.devaxiom.safedocs.dto.document.BulkDeleteDocumentsResponse;
import org.devaxiom.safedocs.dto.document.BulkUpdateDocumentSubjectRequest;
import org.devaxiom.safedocs.dto.document.BulkUpdateDocumentSubjectResponse;
import org.devaxiom.safedocs.dto.document.CreateDocumentRequest;
import org.devaxiom.safedocs.dto.document.DocumentPageResponse;
import org.devaxiom.safedocs.dto.document.DocumentReconcileRequest;
import org.devaxiom.safedocs.dto.document.DocumentReconcileResponse;
import org.devaxiom.safedocs.dto.document.DocumentResponse;
import org.devaxiom.safedocs.dto.document.DocumentShareResponse;
import org.devaxiom.safedocs.dto.document.UpdateDocumentRequest;
import org.devaxiom.safedocs.dto.document.UpdateDocumentSubjectRequest;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.DocumentService;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final PrincipleUserService principleUserService;

    @PostMapping
    public BaseResponseEntity<DocumentResponse> createDocument(
            @Valid @RequestBody CreateDocumentRequest request) {
        User user = requireUser();
        DocumentResponse resp = documentService.upsertDocument(request, user);
        return ResponseBuilder.success(resp, "Document saved");
    }

    @PutMapping("/{id}")
    public BaseResponseEntity<DocumentResponse> updateDocument(
            @PathVariable("id") String id,
            @Valid @RequestBody UpdateDocumentRequest request) {
        User user = requireUser();
        DocumentResponse resp = documentService.updateDocument(parseId(id), request, user);
        return ResponseBuilder.success(resp, "Document updated");
    }

    @GetMapping
    public BaseResponseEntity<DocumentPageResponse> list(
            @RequestParam(value = "visibility", required = false) DocumentVisibility visibility,
            @RequestParam(value = "type", required = false) DocumentVisibility type,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @RequestParam(value = "familyId", required = false) String familyId,
            @RequestParam(value = "subjectId", required = false) String subjectId,
            @RequestParam(value = "uncategorized", defaultValue = "false") boolean uncategorized,
            @RequestParam(value = "createdAfter", required = false) String createdAfter,
            @RequestParam(value = "createdBefore", required = false) String createdBefore) {
        if (type != null) {
            throw new BadRequestException("type is deprecated; use visibility");
        }
        if (uncategorized && subjectId != null) {
            throw new BadRequestException("Use either subjectId or uncategorized=true, not both");
        }
        User user = requireUser();

        LocalDateTime createdAfterLdt = createdAfter != null ? parseDateStart(createdAfter) : null;
        LocalDateTime createdBeforeLdt = createdBefore != null ? parseDateEndExclusive(createdBefore) : null;

        DocumentService.DocumentFilter filter = new DocumentService.DocumentFilter(
                visibility,
                category,
                search,
                page,
                size,
                familyId != null ? parseId(familyId) : null,
                subjectId != null ? parseId(subjectId) : null,
                uncategorized,
                createdAfterLdt,
                createdBeforeLdt
        );
        DocumentPageResponse resp = documentService.listWithFilters(filter, user);
        return ResponseBuilder.success(resp, "Documents fetched");
    }

    @PatchMapping("/subject/bulk")
    public BaseResponseEntity<BulkUpdateDocumentSubjectResponse> bulkUpdateSubject(
            @Valid @RequestBody BulkUpdateDocumentSubjectRequest request
    ) {
        User user = requireUser();
        BulkUpdateDocumentSubjectResponse resp = documentService.bulkUpdateDocumentSubject(request, user);
        return ResponseBuilder.success(resp, "Documents updated");
    }

    @DeleteMapping("/bulk")
    public BaseResponseEntity<BulkDeleteDocumentsResponse> bulkDelete(
            @Valid @RequestBody BulkDeleteDocumentsRequest request
    ) {
        User user = requireUser();
        BulkDeleteDocumentsResponse resp = documentService.bulkDeleteDocuments(request, user);
        return ResponseBuilder.success(resp, "Documents deleted");
    }

    @PatchMapping("/{id}/subject")
    public BaseResponseEntity<DocumentResponse> updateSubject(
            @PathVariable("id") String id,
            @RequestBody UpdateDocumentSubjectRequest request
    ) {
        User user = requireUser();
        DocumentResponse resp = documentService.updateDocumentSubject(parseId(id), request, user);
        return ResponseBuilder.success(resp, "Document updated");
    }

    @GetMapping("/shared/with-me")
    public BaseResponseEntity<DocumentPageResponse> sharedWithMe(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        User user = requireUser();
        DocumentPageResponse items = documentService.listSharedWith(user, page, size, search);
        return ResponseBuilder.success(items, "Shared documents fetched");
    }

    @GetMapping("/{id}")
    public BaseResponseEntity<DocumentResponse> getDocument(@PathVariable("id") String id) {
        User user = requireUser();
        DocumentResponse resp = documentService.getDocument(parseId(id), user);
        return ResponseBuilder.success(resp, "Document fetched");
    }

    @DeleteMapping("/{id}")
    public BaseResponseEntity<?> deleteDocument(@PathVariable("id") String id) {
        User user = requireUser();
        documentService.deleteDocument(parseId(id), user);
        return ResponseBuilder.success("Document deleted");
    }

    @PostMapping("/{id}/share")
    public BaseResponseEntity<List<DocumentShareResponse>> addShare(
            @PathVariable("id") String id,
            @Valid @RequestBody AddShareRequest request) {
        User user = requireUser();
        List<DocumentShareResponse> responses = documentService.addShares(parseId(id), request.emails(), user);
        return ResponseBuilder.success(responses, "Share entries added");
    }

    @DeleteMapping("/{id}/share/{shareId}")
    public BaseResponseEntity<?> removeShare(
            @PathVariable("id") String id,
            @PathVariable("shareId") Long shareId) {
        User user = requireUser();
        documentService.removeShare(parseId(id), shareId, user);
        return ResponseBuilder.success("Share entry removed");
    }

    @GetMapping("/{id}/share")
    public BaseResponseEntity<List<DocumentShareResponse>> listShares(
            @PathVariable("id") String id) {
        User user = requireUser();
        List<DocumentShareResponse> shares = documentService.listShares(parseId(id), user);
        return ResponseBuilder.success(shares, "Share recipients fetched");
    }

    @PostMapping("/reconcile")
    public BaseResponseEntity<DocumentReconcileResponse> reconcile(
            @Valid @RequestBody DocumentReconcileRequest request) {
        User user = requireUser();
        DocumentReconcileResponse resp = documentService.reconcile(request, user);
        return ResponseBuilder.success(resp, "Reconciliation applied");
    }

    @GetMapping("/{id}/download")
    public BaseResponseEntity<?> download(@PathVariable("id") String id) {
        requireUser();
        BaseResponse<Object> body = BaseResponse.builder()
                .success(false)
                .message("Stored in Google Drive. Use Drive API.")
                .build();
        return new BaseResponseEntity<>(body, HttpStatus.GONE);
    }

    @PostMapping("/{id}/downloaded")
    public BaseResponseEntity<?> downloaded(@PathVariable("id") String id) {
        User user = requireUser();
        documentService.markDownloaded(parseId(id), user);
        return ResponseBuilder.success("Download recorded");
    }

    private User requireUser() {
        return principleUserService.getCurrentUser()
                .orElseThrow(() -> new BadRequestException("Unauthorized"));
    }

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid document id");
        }
    }

    private LocalDateTime parseDateStart(String raw) {
        try {
            LocalDate date = LocalDate.parse(raw);
            return date.atStartOfDay();
        } catch (Exception ex) {
            throw new BadRequestException("Invalid createdAfter; expected yyyy-MM-dd");
        }
    }

    private LocalDateTime parseDateEndExclusive(String raw) {
        try {
            LocalDate date = LocalDate.parse(raw);
            return date.plusDays(1).atStartOfDay();
        } catch (Exception ex) {
            throw new BadRequestException("Invalid createdBefore; expected yyyy-MM-dd");
        }
    }
}
