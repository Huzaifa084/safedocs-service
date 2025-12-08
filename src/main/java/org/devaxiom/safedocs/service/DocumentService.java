package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.dto.document.DocumentListItem;
import org.devaxiom.safedocs.dto.document.DocumentResponse;
import org.devaxiom.safedocs.dto.document.DocumentPageResponse;
import org.devaxiom.safedocs.dto.document.DocumentShareResponse;
import org.devaxiom.safedocs.enums.DocumentShareStatus;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.enums.FamilyRole;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.exception.ResourceNotFoundException;
import org.devaxiom.safedocs.exception.UnauthorizedException;
import org.devaxiom.safedocs.model.*;
import org.devaxiom.safedocs.repository.DocumentRepository;
import org.devaxiom.safedocs.repository.DocumentShareRepository;
import org.devaxiom.safedocs.repository.FamilyMemberRepository;
import org.devaxiom.safedocs.repository.UserRepository;
import org.devaxiom.safedocs.storage.StorageContext;
import org.devaxiom.safedocs.storage.StorageService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "expiryDate", "title");
    private static final Set<String> ALLOWED_DOC_TYPES = Set.of(
            // Images (common Android/iOS + web)
            "application/pdf",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/tiff",
            "image/jpg",
            "image/webp",
            "image/bmp",
            "image/svg+xml",
            "image/heic",
            "image/heif",
            // Office docs
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            // Text / CSV
            "text/plain",
            "text/csv",
            // Archives
            "application/zip",
            "application/x-7z-compressed",
            "application/x-rar-compressed",
            // Videos (most common)
            "video/mp4",
            "video/quicktime"
    );

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository documentShareRepository;
    private final FamilyService familyService;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    public DocumentResponse createDocument(DocumentCommand cmd, MultipartFile file, User currentUser) {
        if (currentUser == null) throw new UnauthorizedException("Unauthorized");
        if (file == null || file.isEmpty()) throw new BadRequestException("File is required");
        validateCommand(cmd);

        Document doc = new Document();
        doc.setOwner(currentUser);
        doc.setVisibility(cmd.visibility());
        doc.setTitle(cmd.title());
        doc.setCategory(cmd.category());
        doc.setExpiryDate(cmd.expiryDate());
        if (cmd.visibility() == DocumentVisibility.FAMILY) {
            Family family = familyService.ensureFamilyForUser(currentUser);
            doc.setFamily(family);
        }
        doc.setStatus(DocumentStatus.ACTIVE);
        doc.setPublicId(UUID.randomUUID());

        var upload = storageService.uploadFile(
                StorageContext.DOCUMENTS,
                doc.getPublicId().toString(),
                file,
                currentUser.getFullName(),
                ALLOWED_DOC_TYPES
        );
        doc.setStorageKey(upload.key());
        doc.setStorageFilename(upload.filename());
        doc.setStorageMimeType(upload.mimeType());
        doc.setStorageSizeBytes(upload.size());

        doc = documentRepository.save(doc);

        if (doc.getVisibility() == DocumentVisibility.SHARED && cmd.shareWith() != null && !cmd.shareWith().isEmpty()) {
            addShares(doc, cmd.shareWith());
        }

        return toResponse(doc);
    }

    public DocumentResponse updateDocument(UUID documentId, DocumentCommand cmd, MultipartFile file, User currentUser) {
        Document doc = getActiveDocument(documentId);
        assertCanModify(doc, currentUser);

        if (cmd.title() != null) doc.setTitle(cmd.title());
        if (cmd.category() != null) doc.setCategory(cmd.category());
        if (cmd.expiryDate() != null) doc.setExpiryDate(cmd.expiryDate());

        if (file != null && !file.isEmpty()) {
            var upload = storageService.uploadFile(
                    StorageContext.DOCUMENTS,
                    doc.getPublicId().toString(),
                    file,
                    currentUser.getFullName(),
                    ALLOWED_DOC_TYPES
            );
            doc.setStorageKey(upload.key());
            doc.setStorageFilename(upload.filename());
            doc.setStorageMimeType(upload.mimeType());
            doc.setStorageSizeBytes(upload.size());
        }

        doc = documentRepository.save(doc);

        if (doc.getVisibility() == DocumentVisibility.SHARED && cmd.shareWith() != null) {
            addShares(doc, cmd.shareWith());
        }

        return toResponse(doc);
    }

    public DocumentResponse getDocument(UUID documentId, User user) {
        Document doc = getActiveDocument(documentId);
        assertCanView(doc, user);
        return toResponse(doc);
    }

    public List<DocumentListItem> listPersonal(User user) {
        List<Document> docs = documentRepository.findByOwnerIdAndVisibilityAndStatus(
                user.getId(), DocumentVisibility.PERSONAL, DocumentStatus.ACTIVE, DEFAULT_SORT);
        return docs.stream().map(this::toListItem).toList();
    }

    public List<DocumentListItem> listFamily(User user) {
        Optional<FamilyMember> membership = familyMemberRepository.findByUserIdAndActiveTrue(user.getId());
        if (membership.isEmpty()) return List.of();
        Long familyId = membership.get().getFamily().getId();
        List<Document> docs = documentRepository.findByFamilyIdAndStatus(familyId, DocumentStatus.ACTIVE, DEFAULT_SORT);
        return docs.stream().filter(d -> d.getVisibility() == DocumentVisibility.FAMILY).map(this::toListItem).toList();
    }

    public List<DocumentListItem> listSharedBy(User user) {
        List<Document> docs = documentRepository.findByOwnerIdAndVisibilityAndStatus(
                user.getId(), DocumentVisibility.SHARED, DocumentStatus.ACTIVE, DEFAULT_SORT);
        return docs.stream().map(this::toListItem).toList();
    }

    public DocumentPageResponse listSharedWith(User user, int page, int size, String search) {
        String email = normalizeEmail(user.getEmail());
        List<DocumentShare> shares = documentShareRepository.findByRecipientEmailAndStatus(
                email, DocumentShareStatus.ACTIVE);
        List<Document> docs = shares.stream()
                .map(DocumentShare::getDocument)
                .filter(doc -> doc.getStatus() == DocumentStatus.ACTIVE)
                .filter(doc -> matchesSearch(doc, search))
                .toList();

        int pageIndex = Math.max(0, page);
        int pageSize = size <= 0 ? 20 : size;
        int from = pageIndex * pageSize;
        int to = Math.min(from + pageSize, docs.size());
        List<DocumentListItem> items = from >= docs.size()
                ? List.of()
                : docs.subList(from, to).stream().map(this::toListItem).toList();
        return new DocumentPageResponse(items, pageIndex, pageSize, docs.size());
    }

    public DocumentPageResponse listWithFilters(DocumentFilter filter, User user) {
        if (filter.visibility == null) throw new BadRequestException("type is required");
        List<Document> base = switch (filter.visibility) {
            case PERSONAL -> documentRepository.findByOwnerIdAndVisibilityAndStatus(
                    user.getId(), DocumentVisibility.PERSONAL, DocumentStatus.ACTIVE, DEFAULT_SORT);
            case FAMILY -> {
                Optional<FamilyMember> membership = familyMemberRepository.findByUserIdAndActiveTrue(user.getId());
                if (membership.isEmpty()) yield List.of();
                Long familyId = membership.get().getFamily().getId();
                yield documentRepository.findByFamilyIdAndStatus(familyId, DocumentStatus.ACTIVE, DEFAULT_SORT)
                        .stream()
                        .filter(d -> d.getVisibility() == DocumentVisibility.FAMILY)
                        .toList();
            }
            case SHARED -> {
                // shared by me
                List<Document> owned = documentRepository.findByOwnerIdAndVisibilityAndStatus(
                        user.getId(), DocumentVisibility.SHARED, DocumentStatus.ACTIVE, DEFAULT_SORT);
                // shared with me
                List<DocumentShare> shares = documentShareRepository.findByRecipientEmailAndStatus(
                        normalizeEmail(user.getEmail()), DocumentShareStatus.ACTIVE);
                List<Document> withMe = shares.stream()
                        .map(DocumentShare::getDocument)
                        .filter(doc -> doc.getStatus() == DocumentStatus.ACTIVE)
                        .toList();
                Set<Document> combined = new HashSet<>();
                combined.addAll(owned);
                combined.addAll(withMe);
                yield combined.stream().sorted((a, b) -> {
                    LocalDate ea = a.getExpiryDate();
                    LocalDate eb = b.getExpiryDate();
                    if (ea == null && eb == null) return a.getTitle().compareToIgnoreCase(b.getTitle());
                    if (ea == null) return 1;
                    if (eb == null) return -1;
                    return ea.compareTo(eb);
                }).toList();
            }
        };

        List<Document> filtered = base.stream()
                .filter(d -> filter.category == null || (d.getCategory() != null && d.getCategory().equalsIgnoreCase(filter.category)))
                .filter(d -> filter.search == null || matchesSearch(d, filter.search))
                .filter(d -> {
                    if (filter.expiryFrom == null && filter.expiryTo == null) return true;
                    LocalDate e = d.getExpiryDate();
                    if (e == null) return false;
                    boolean after = filter.expiryFrom == null || !e.isBefore(filter.expiryFrom);
                    boolean before = filter.expiryTo == null || !e.isAfter(filter.expiryTo);
                    return after && before;
                })
                .toList();

        int page = Math.max(0, filter.page);
        int size = filter.size <= 0 ? 20 : filter.size;
        int from = page * size;
        int to = Math.min(from + size, filtered.size());
        List<DocumentListItem> items = from >= filtered.size()
                ? List.of()
                : filtered.subList(from, to).stream().map(this::toListItem).toList();

        return new DocumentPageResponse(items, page, size, filtered.size());
    }

    public void deleteDocument(UUID documentId, User user) {
        Document doc = getActiveDocument(documentId);
        assertCanModify(doc, user);
        doc.setStatus(DocumentStatus.DELETED);
        documentRepository.save(doc);
    }

    public List<DocumentShareResponse> addShares(UUID documentId, List<String> emails, User currentUser) {
        Document doc = getActiveDocument(documentId);
        assertOwnerForShared(doc, currentUser);
        return addShares(doc, emails);
    }

    public List<DocumentShareResponse> listShares(UUID documentId, User currentUser) {
        Document doc = getActiveDocument(documentId);
        assertOwnerForShared(doc, currentUser);
        return documentShareRepository.findByDocumentIdAndStatus(doc.getId(), DocumentShareStatus.ACTIVE)
                .stream()
                .map(ds -> new DocumentShareResponse(ds.getId(), ds.getRecipientEmail(),
                        Boolean.TRUE.equals(ds.getCanEdit()), ds.getStatus()))
                .toList();
    }

    public void removeShare(UUID documentId, Long shareId, User currentUser) {
        Document doc = getActiveDocument(documentId);
        assertOwnerForShared(doc, currentUser);
        DocumentShare share = documentShareRepository.findByIdAndDocumentId(shareId, doc.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Share entry not found"));
        share.setStatus(DocumentShareStatus.REVOKED);
        documentShareRepository.save(share);
    }

    public ResponseEntity<InputStreamResource> download(UUID documentId, User user) {
        Document doc = getActiveDocument(documentId);
        assertCanView(doc, user);
        return storageService.downloadFile(doc.getStorageKey());
    }

    private List<DocumentShareResponse> addShares(Document doc, List<String> emails) {
        if (doc.getVisibility() != DocumentVisibility.SHARED) {
            throw new BadRequestException("Sharing allowed only for SHARED documents");
        }
        List<DocumentShareResponse> created = new ArrayList<>();
        for (String emailRaw : emails) {
            if (emailRaw == null || emailRaw.isBlank()) continue;
            String email = normalizeEmail(emailRaw);
            if (documentShareRepository.existsByDocumentIdAndRecipientEmailAndStatus(doc.getId(), email, DocumentShareStatus.ACTIVE)) {
                continue;
            }
            DocumentShare share = new DocumentShare();
            share.setDocument(doc);
            share.setRecipientEmail(email);
            share.setStatus(DocumentShareStatus.ACTIVE);
            share.setCanEdit(false);
            userRepository.findByEmail(email).ifPresent(share::setRecipientUser);
            share = documentShareRepository.save(share);
            created.add(new DocumentShareResponse(share.getId(), share.getRecipientEmail(),
                    Boolean.TRUE.equals(share.getCanEdit()), share.getStatus()));
        }
        return created;
    }

    private Document getActiveDocument(UUID publicId) {
        return documentRepository.findByPublicId(publicId)
                .filter(d -> d.getStatus() == DocumentStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
    }

    private void assertCanView(Document doc, User user) {
        if (user == null) throw new UnauthorizedException("Unauthorized");
        switch (doc.getVisibility()) {
            case PERSONAL -> {
                if (!Objects.equals(doc.getOwner().getId(), user.getId())) {
                    throw new UnauthorizedException("Not allowed to view this document");
                }
            }
            case FAMILY -> {
                Family family = doc.getFamily();
                if (family == null) throw new UnauthorizedException("Family access not configured");
                boolean member = familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), user.getId()).isPresent();
                if (!member) throw new UnauthorizedException("Not allowed to view this family document");
            }
            case SHARED -> {
                if (Objects.equals(doc.getOwner().getId(), user.getId())) return;
                String email = normalizeEmail(user.getEmail());
                boolean shared = documentShareRepository.existsByDocumentIdAndRecipientEmailAndStatus(
                        doc.getId(), email, DocumentShareStatus.ACTIVE);
                if (!shared) throw new UnauthorizedException("Not allowed to view this shared document");
            }
            default -> throw new UnauthorizedException("Unknown visibility");
        }
    }

    private void assertCanModify(Document doc, User user) {
        if (user == null) throw new UnauthorizedException("Unauthorized");
        if (doc.getVisibility() == DocumentVisibility.PERSONAL) {
            if (!Objects.equals(doc.getOwner().getId(), user.getId())) {
                throw new UnauthorizedException("Not allowed to modify this document");
            }
            return;
        }
        if (doc.getVisibility() == DocumentVisibility.FAMILY) {
            if (Objects.equals(doc.getOwner().getId(), user.getId())) return;
            Family family = doc.getFamily();
            if (family == null) throw new UnauthorizedException("Family access not configured");
            boolean head = familyMemberRepository.findByFamilyIdAndRoleAndActiveTrue(family.getId(), FamilyRole.HEAD)
                    .stream()
                    .anyMatch(m -> Objects.equals(m.getUser().getId(), user.getId()));
            if (!head) throw new UnauthorizedException("Only family head or owner can modify");
            return;
        }
        if (doc.getVisibility() == DocumentVisibility.SHARED) {
            if (!Objects.equals(doc.getOwner().getId(), user.getId())) {
                throw new UnauthorizedException("Only owner can modify a shared document");
            }
        }
    }

    private void assertOwnerForShared(Document doc, User user) {
        if (doc.getVisibility() != DocumentVisibility.SHARED) {
            throw new BadRequestException("Sharing allowed only for SHARED documents");
        }
        if (user == null || !Objects.equals(doc.getOwner().getId(), user.getId())) {
            throw new UnauthorizedException("Only owner can manage sharing");
        }
    }

    private void validateCommand(DocumentCommand cmd) {
        if (cmd.title() == null || cmd.title().isBlank()) throw new BadRequestException("Title is required");
        if (cmd.visibility() == null) throw new BadRequestException("Visibility is required");
    }

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(
                doc.getPublicId(),
                doc.getTitle(),
                doc.getCategory(),
                doc.getVisibility(),
                doc.getExpiryDate(),
                doc.getStorageKey(),
                doc.getStorageFilename(),
                doc.getStorageSizeBytes(),
                doc.getStorageMimeType(),
                doc.getOwner() != null ? doc.getOwner().getFullName() : null
        );
    }

    private boolean matchesSearch(Document doc, String search) {
        if (search == null || search.isBlank()) return true;
        String term = search.trim().toLowerCase();
        return (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(term))
                || (doc.getCategory() != null && doc.getCategory().toLowerCase().contains(term));
    }

    private DocumentListItem toListItem(Document doc) {
        return new DocumentListItem(
                doc.getPublicId(),
                doc.getTitle(),
                doc.getCategory(),
                doc.getVisibility(),
                doc.getExpiryDate(),
                doc.getOwner() != null ? doc.getOwner().getFullName() : null
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    public record DocumentCommand(
            String title,
            String category,
            DocumentVisibility visibility,
            LocalDate expiryDate,
            List<String> shareWith
    ) {
    }

    public record DocumentFilter(
            DocumentVisibility visibility,
            String category,
            String search,
            LocalDate expiryFrom,
            LocalDate expiryTo,
            int page,
            int size
    ) {
    }
}
