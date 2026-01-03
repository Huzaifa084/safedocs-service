package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.dto.document.CreateDocumentRequest;
import org.devaxiom.safedocs.dto.document.DocumentListItem;
import org.devaxiom.safedocs.dto.document.DocumentPageResponse;
import org.devaxiom.safedocs.dto.document.DocumentReconcileRequest;
import org.devaxiom.safedocs.dto.document.DocumentReconcileResponse;
import org.devaxiom.safedocs.dto.document.DocumentResponse;
import org.devaxiom.safedocs.dto.document.DocumentShareResponse;
import org.devaxiom.safedocs.dto.document.UpdateDocumentRequest;
import org.devaxiom.safedocs.enums.DocumentReferenceType;
import org.devaxiom.safedocs.enums.DocumentShareStatus;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.enums.PermissionJobAction;
import org.devaxiom.safedocs.enums.StorageProvider;
import org.devaxiom.safedocs.enums.FamilyRole;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.exception.ResourceNotFoundException;
import org.devaxiom.safedocs.exception.UnauthorizedException;
import org.devaxiom.safedocs.model.Document;
import org.devaxiom.safedocs.model.DocumentShare;
import org.devaxiom.safedocs.model.Family;
import org.devaxiom.safedocs.model.FamilyMember;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.repository.DocumentRepository;
import org.devaxiom.safedocs.repository.DocumentShareRepository;
import org.devaxiom.safedocs.repository.FamilyMemberRepository;
import org.devaxiom.safedocs.repository.FamilyRepository;
import org.devaxiom.safedocs.repository.UserRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

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

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "title");

    private final DocumentRepository documentRepository;
    private final DocumentShareRepository documentShareRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyRepository familyRepository;
    private final UserRepository userRepository;
    private final PermissionJobService permissionJobService;

    public DocumentResponse upsertDocument(CreateDocumentRequest request, User currentUser) {
        if (currentUser == null) throw new UnauthorizedException("Unauthorized");
        validateCreateRequest(request);

        String driveFileId = normalizeId(request.driveFileId());
        Document doc = documentRepository.findByOwnerIdAndDriveFileId(currentUser.getId(), driveFileId)
                .orElseGet(Document::new);
        boolean isNew = doc.getId() == null;

        DocumentVisibility oldVisibility = doc.getVisibility();
        Family oldFamily = doc.getFamily();

        if (isNew) {
            doc.setOwner(currentUser);
            doc.setPublicId(UUID.randomUUID());
        }

        doc.setDriveFileId(driveFileId);
        doc.setFileName(trimOrNull(request.fileName()));
        doc.setTitle(resolveTitle(request.title(), request.fileName()));
        doc.setMimeType(trimOrNull(request.mimeType()));
        doc.setSizeBytes(request.sizeBytes());
        doc.setCategory(trimOrNull(request.category()));
        doc.setDriveCreatedAt(request.driveCreatedAt());
        doc.setDriveWebViewLink(trimOrNull(request.driveWebViewLink()));
        doc.setDriveMd5(trimOrNull(request.driveMd5()));
        doc.setStorageProvider(StorageProvider.DRIVE);
        if (request.referenceType() != null) {
            doc.setReferenceType(request.referenceType());
        } else if (doc.getReferenceType() == null) {
            doc.setReferenceType(DocumentReferenceType.FILE);
        }
        if (request.accessLevel() != null) {
            doc.setAccessLevel(request.accessLevel());
        }
        if (doc.getStatus() == null || doc.getStatus() == DocumentStatus.DELETED_OR_REVOKED) {
            doc.setStatus(DocumentStatus.ACTIVE);
        }

        Family newFamily = resolveFamilyForVisibility(request.visibility(), request.familyId(), currentUser, doc);
        doc.setVisibility(request.visibility());
        doc.setFamily(newFamily);

        doc = documentRepository.save(doc);

        enqueueFamilyJobsOnVisibilityChange(doc, doc.getOwner(), oldVisibility, oldFamily, request.visibility(), newFamily);

        return toResponse(doc);
    }

    public DocumentResponse updateDocument(UUID documentId, UpdateDocumentRequest request, User currentUser) {
        Document doc = getActiveDocument(documentId);
        assertCanModify(doc, currentUser);
        enforceImmutableUpdates(request);

        if (request.title() != null) doc.setTitle(request.title());
        if (request.category() != null) doc.setCategory(request.category());

        DocumentVisibility newVisibility = request.visibility() != null ? request.visibility() : doc.getVisibility();
        UUID newFamilyId = request.familyId();
        DocumentVisibility oldVisibility = doc.getVisibility();
        Family oldFamily = doc.getFamily();

        Family newFamily = resolveFamilyForVisibility(newVisibility, newFamilyId, currentUser, doc);
        doc.setVisibility(newVisibility);
        doc.setFamily(newFamily);

        doc = documentRepository.save(doc);

        enqueueFamilyJobsOnVisibilityChange(doc, doc.getOwner(), oldVisibility, oldFamily, newVisibility, newFamily);

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
        List<FamilyMember> memberships = familyMemberRepository.findByUserIdAndActiveTrue(user.getId());
        if (memberships.isEmpty()) return List.of();
        List<Long> familyIds = memberships.stream().map(m -> m.getFamily().getId()).toList();
        List<Document> docs = documentRepository.findByFamilyIdInAndStatus(familyIds, DocumentStatus.ACTIVE, DEFAULT_SORT);
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
        if (filter.visibility == null) throw new BadRequestException("visibility is required");
        List<Document> base = switch (filter.visibility) {
            case PERSONAL -> documentRepository.findByOwnerIdAndVisibilityAndStatus(
                    user.getId(), DocumentVisibility.PERSONAL, DocumentStatus.ACTIVE, DEFAULT_SORT);
            case FAMILY -> {
                List<FamilyMember> memberships = familyMemberRepository.findByUserIdAndActiveTrue(user.getId());
                if (memberships.isEmpty()) yield List.of();
                List<Long> familyIds = memberships.stream()
                        .map(m -> m.getFamily().getId())
                        .toList();
                if (filter.familyId != null) {
                    Family family = familyRepository.findByPublicId(filter.familyId)
                            .orElseThrow(() -> new BadRequestException("Family not found"));
                    if (!familyIds.contains(family.getId())) {
                        throw new UnauthorizedException("Not a member of this family");
                    }
                    familyIds = List.of(family.getId());
                }
                yield documentRepository.findByFamilyIdInAndStatus(familyIds, DocumentStatus.ACTIVE, DEFAULT_SORT)
                        .stream()
                        .filter(d -> d.getVisibility() == DocumentVisibility.FAMILY)
                        .toList();
            }
            case SHARED -> {
                List<Document> owned = documentRepository.findByOwnerIdAndVisibilityAndStatus(
                        user.getId(), DocumentVisibility.SHARED, DocumentStatus.ACTIVE, DEFAULT_SORT);
                List<DocumentShare> shares = documentShareRepository.findByRecipientEmailAndStatus(
                        normalizeEmail(user.getEmail()), DocumentShareStatus.ACTIVE);
                List<Document> withMe = shares.stream()
                        .map(DocumentShare::getDocument)
                        .filter(doc -> doc.getStatus() == DocumentStatus.ACTIVE)
                        .toList();
                Set<Document> combined = new HashSet<>();
                combined.addAll(owned);
                combined.addAll(withMe);
                yield combined.stream().sorted((a, b) -> a.getTitle().compareToIgnoreCase(b.getTitle())).toList();
            }
        };

        List<Document> filtered = base.stream()
                .filter(d -> filter.category == null || (d.getCategory() != null && d.getCategory().equalsIgnoreCase(filter.category)))
                .filter(d -> filter.search == null || matchesSearch(d, filter.search))
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

        if (doc.getVisibility() == DocumentVisibility.FAMILY) {
            enqueueFamilyJobs(doc, doc.getOwner(), doc.getFamily(), PermissionJobAction.REVOKE);
        } else if (doc.getVisibility() == DocumentVisibility.SHARED) {
            enqueueShareJobs(doc, doc.getOwner(), PermissionJobAction.REVOKE);
        }

        doc.setStatus(DocumentStatus.DELETED_OR_REVOKED);
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
        permissionJobService.enqueueJob(doc, doc.getOwner(), share.getRecipientEmail(), PermissionJobAction.REVOKE, null);
    }

    public DocumentReconcileResponse reconcile(DocumentReconcileRequest request, User currentUser) {
        int updated = 0;
        for (DocumentReconcileRequest.MissingDocument missing : request.missing()) {
            Document doc = resolveDocumentForReconcile(missing, currentUser);
            if (doc != null && doc.getStatus() != DocumentStatus.DELETED_OR_REVOKED) {
                doc.setStatus(DocumentStatus.DELETED_OR_REVOKED);
                documentRepository.save(doc);
                updated++;
            }
        }
        return new DocumentReconcileResponse(updated);
    }

    private Document resolveDocumentForReconcile(DocumentReconcileRequest.MissingDocument missing, User currentUser) {
        if (missing.publicId() != null) {
            Document doc = documentRepository.findByPublicId(missing.publicId())
                    .orElse(null);
            if (doc == null) return null;
            if (!Objects.equals(doc.getOwner().getId(), currentUser.getId())) {
                throw new UnauthorizedException("Not allowed to reconcile this document");
            }
            return doc;
        }
        if (missing.driveFileId() != null && !missing.driveFileId().isBlank()) {
            return documentRepository.findByOwnerIdAndDriveFileId(currentUser.getId(), missing.driveFileId().trim())
                    .orElse(null);
        }
        throw new BadRequestException("Each missing item must include publicId or driveFileId");
    }

    private List<DocumentShareResponse> addShares(Document doc, List<String> emails) {
        if (doc.getVisibility() != DocumentVisibility.SHARED) {
            throw new BadRequestException("Sharing allowed only for SHARED documents");
        }
        List<DocumentShareResponse> created = new ArrayList<>();
        for (String emailRaw : emails) {
            if (emailRaw == null || emailRaw.isBlank()) continue;
            String email = normalizeEmail(emailRaw);
            if (email == null || email.isBlank()) continue;

            Optional<DocumentShare> existing = documentShareRepository.findByDocumentIdAndRecipientEmailAndStatus(
                    doc.getId(), email, DocumentShareStatus.ACTIVE);
            if (existing.isPresent()) {
                continue;
            }

            DocumentShare share = documentShareRepository.findByDocumentIdAndRecipientEmailAndStatus(
                    doc.getId(), email, DocumentShareStatus.REVOKED).orElse(null);
            if (share == null) {
                share = new DocumentShare();
                share.setDocument(doc);
                share.setRecipientEmail(email);
            }
            share.setStatus(DocumentShareStatus.ACTIVE);
            share.setCanEdit(false);
            userRepository.findByEmail(email).ifPresent(share::setRecipientUser);
            share = documentShareRepository.save(share);
            created.add(new DocumentShareResponse(share.getId(), share.getRecipientEmail(),
                    Boolean.TRUE.equals(share.getCanEdit()), share.getStatus()));
            permissionJobService.enqueueJob(doc, doc.getOwner(), email, PermissionJobAction.GRANT, null);
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

    private void validateCreateRequest(CreateDocumentRequest request) {
        if (request.driveFileId() == null || request.driveFileId().isBlank()) {
            throw new BadRequestException("driveFileId is required");
        }
        if (request.fileName() == null || request.fileName().isBlank()) {
            throw new BadRequestException("fileName is required");
        }
        if (request.visibility() == null) throw new BadRequestException("visibility is required");
    }

    private void enforceImmutableUpdates(UpdateDocumentRequest request) {
        if (request.driveFileId() != null
                || request.fileName() != null
                || request.referenceType() != null
                || request.storageProvider() != null
                || request.accessLevel() != null) {
            throw new BadRequestException("Drive fields are immutable and can only be set during creation");
        }
    }

    private void enqueueFamilyJobsOnVisibilityChange(
            Document doc,
            User owner,
            DocumentVisibility oldVisibility,
            Family oldFamily,
            DocumentVisibility newVisibility,
            Family newFamily
    ) {
        if (oldVisibility == DocumentVisibility.FAMILY && (newVisibility != DocumentVisibility.FAMILY || !sameFamily(oldFamily, newFamily))) {
            enqueueFamilyJobs(doc, owner, oldFamily, PermissionJobAction.REVOKE);
        }
        if (newVisibility == DocumentVisibility.FAMILY && (oldVisibility != DocumentVisibility.FAMILY || !sameFamily(oldFamily, newFamily))) {
            enqueueFamilyJobs(doc, owner, newFamily, PermissionJobAction.GRANT);
        }
    }

    private boolean sameFamily(Family left, Family right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        return Objects.equals(left.getId(), right.getId());
    }

    private void enqueueFamilyJobs(Document doc, User owner, Family family, PermissionJobAction action) {
        if (family == null) return;
        List<FamilyMember> members = familyMemberRepository.findByFamilyIdAndActiveTrue(family.getId());
        for (FamilyMember member : members) {
            if (member.getUser() == null || member.getUser().getEmail() == null) continue;
            String email = normalizeEmail(member.getUser().getEmail());
            if (email == null) continue;
            if (owner.getEmail() != null && email.equalsIgnoreCase(owner.getEmail())) continue;
            permissionJobService.enqueueJob(doc, owner, email, action, family);
        }
    }

    private void enqueueShareJobs(Document doc, User owner, PermissionJobAction action) {
        List<DocumentShare> shares = documentShareRepository.findByDocumentIdAndStatus(doc.getId(), DocumentShareStatus.ACTIVE);
        for (DocumentShare share : shares) {
            if (share.getRecipientEmail() == null) continue;
            String email = normalizeEmail(share.getRecipientEmail());
            if (email == null) continue;
            if (owner.getEmail() != null && email.equalsIgnoreCase(owner.getEmail())) continue;
            permissionJobService.enqueueJob(doc, owner, email, action, null);
            if (action == PermissionJobAction.REVOKE) {
                share.setStatus(DocumentShareStatus.REVOKED);
                documentShareRepository.save(share);
            }
        }
    }

    private Family resolveFamilyForVisibility(DocumentVisibility visibility, UUID familyPublicId, User user, Document doc) {
        if (visibility == null) {
            throw new BadRequestException("visibility is required");
        }
        if (visibility == DocumentVisibility.FAMILY) {
            if (familyPublicId == null) {
                if (doc.getFamily() != null) {
                    return doc.getFamily();
                }
                throw new BadRequestException("familyId is required for FAMILY documents");
            }
            return requireFamilyMembership(familyPublicId, user);
        }
        if (familyPublicId != null) {
            throw new BadRequestException("familyId must be null unless visibility is FAMILY");
        }
        return null;
    }

    private DocumentResponse toResponse(Document doc) {
        return new DocumentResponse(
                doc.getPublicId(),
                doc.getDriveFileId(),
                doc.getFileName(),
                doc.getTitle(),
                doc.getMimeType(),
                doc.getSizeBytes(),
                doc.getVisibility(),
                doc.getCategory(),
                doc.getFamily() != null ? doc.getFamily().getPublicId() : null,
                doc.getReferenceType(),
                doc.getStatus(),
                doc.getDriveCreatedAt(),
                doc.getDriveWebViewLink(),
                doc.getDriveMd5(),
                doc.getAccessLevel(),
                doc.getCreatedDate().orElse(null),
                doc.getLastModifiedDate().orElse(null)
        );
    }

    private boolean matchesSearch(Document doc, String search) {
        if (search == null || search.isBlank()) return true;
        String term = search.trim().toLowerCase();
        return (doc.getTitle() != null && doc.getTitle().toLowerCase().contains(term))
                || (doc.getCategory() != null && doc.getCategory().toLowerCase().contains(term))
                || (doc.getFileName() != null && doc.getFileName().toLowerCase().contains(term));
    }

    private DocumentListItem toListItem(Document doc) {
        return new DocumentListItem(
                doc.getPublicId(),
                doc.getDriveFileId(),
                doc.getFileName(),
                doc.getTitle(),
                doc.getMimeType(),
                doc.getSizeBytes(),
                doc.getVisibility(),
                doc.getCategory(),
                doc.getFamily() != null ? doc.getFamily().getPublicId() : null,
                doc.getReferenceType(),
                doc.getStatus()
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String normalizeId(String value) {
        return value == null ? null : value.trim();
    }

    private String trimOrNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String resolveTitle(String title, String fileName) {
        if (title != null && !title.isBlank()) return title.trim();
        return fileName != null ? fileName.trim() : "";
    }

    private Family requireFamilyMembership(UUID familyPublicId, User user) {
        Family family = familyRepository.findByPublicId(familyPublicId)
                .orElseThrow(() -> new BadRequestException("Family not found"));
        boolean member = familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), user.getId()).isPresent();
        if (!member) {
            throw new UnauthorizedException("Not a member of the selected family");
        }
        return family;
    }

    public record DocumentFilter(
            DocumentVisibility visibility,
            String category,
            String search,
            int page,
            int size,
            UUID familyId
    ) {
    }
}
