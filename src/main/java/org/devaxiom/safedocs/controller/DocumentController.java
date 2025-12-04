package org.devaxiom.safedocs.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.base.BaseResponseEntity;
import org.devaxiom.safedocs.dto.base.ResponseBuilder;
import org.devaxiom.safedocs.dto.document.AddShareRequest;
import org.devaxiom.safedocs.dto.document.DocumentResponse;
import org.devaxiom.safedocs.dto.document.DocumentShareResponse;
import org.devaxiom.safedocs.dto.document.DocumentPageResponse;
import org.devaxiom.safedocs.dto.document.DocumentListItem;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.service.DocumentService;
import org.devaxiom.safedocs.service.PrincipleUserService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final PrincipleUserService principleUserService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponseEntity<DocumentResponse> createDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam("visibility") String visibility,
            @RequestParam(value = "expiryDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(value = "shareWith", required = false) List<String> shareWith) {

        User user = principleUserService.getCurrentUser().orElseThrow(()
                -> new BadRequestException("Unauthorized"));
        DocumentVisibility vis = parseVisibility(visibility);
        var cmd = new DocumentService.DocumentCommand(title, category, vis, expiryDate, shareWith);
        DocumentResponse resp = documentService.createDocument(cmd, file, user);
        return ResponseBuilder.success(resp, "Document created");
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponseEntity<DocumentResponse> updateDocument(
            @PathVariable("id") String id,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "expiryDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
            @RequestParam(value = "shareWith", required = false) List<String> shareWith) {

        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        var cmd = new DocumentService.DocumentCommand(title, category, null, expiryDate, shareWith);
        DocumentResponse resp = documentService.updateDocument(parseId(id), cmd, file, user);
        return ResponseBuilder.success(resp, "Document updated");
    }

    @GetMapping
    public BaseResponseEntity<DocumentPageResponse> list(
            @RequestParam("type") String type,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "expiryFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryFrom,
            @RequestParam(value = "expiryTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryTo,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        DocumentVisibility vis = parseVisibility(type);
        DocumentService.DocumentFilter filter = new DocumentService.DocumentFilter(
                vis,
                category,
                search,
                expiryFrom,
                expiryTo,
                page,
                size
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
    public ResponseEntity<InputStreamResource> download(@PathVariable("id") String id) {
        User user = principleUserService.getCurrentUser().orElseThrow(() -> new BadRequestException("Unauthorized"));
        return documentService.download(parseId(id), user);
    }

    private DocumentVisibility parseVisibility(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("Visibility is required");
        try {
            return DocumentVisibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid visibility type");
        }
    }

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid document id");
        }
    }
}
