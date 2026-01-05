package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import org.devaxiom.safedocs.dto.subject.CreateSubjectRequest;
import org.devaxiom.safedocs.dto.subject.SubjectListItem;
import org.devaxiom.safedocs.dto.subject.SubjectPageResponse;
import org.devaxiom.safedocs.dto.subject.UpdateSubjectRequest;
import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.enums.FamilyRole;
import org.devaxiom.safedocs.enums.SubjectScope;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.exception.ForbiddenException;
import org.devaxiom.safedocs.exception.ResourceAlreadyExistsException;
import org.devaxiom.safedocs.exception.ResourceNotFoundException;
import org.devaxiom.safedocs.model.Family;
import org.devaxiom.safedocs.model.Subject;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.repository.DocumentRepository;
import org.devaxiom.safedocs.repository.FamilyMemberRepository;
import org.devaxiom.safedocs.repository.FamilyRepository;
import org.devaxiom.safedocs.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SubjectService {

    private static final Set<DocumentVisibility> PERSONAL_VISIBILITIES = Set.of(DocumentVisibility.PERSONAL, DocumentVisibility.SHARED);

    private final SubjectRepository subjectRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final DocumentRepository documentRepository;

    @Transactional(readOnly = true)
    public SubjectPageResponse list(User user, SubjectScope scope, UUID familyPublicId, int page, int size) {
        if (scope == null) throw new BadRequestException("scope is required");

        List<Subject> all;
        if (scope == SubjectScope.PERSONAL) {
            if (familyPublicId != null) {
                throw new BadRequestException("familyId must be null when scope is PERSONAL");
            }
            all = subjectRepository.findByOwnerIdAndScope(user.getId(), SubjectScope.PERSONAL);
        } else {
            if (familyPublicId == null) throw new BadRequestException("familyId is required when scope is FAMILY");
            Family family = requireFamilyMembership(familyPublicId, user);
            all = subjectRepository.findByFamilyIdAndScope(family.getId(), SubjectScope.FAMILY);
        }

        all = all.stream()
                .sorted(Comparator.comparing(Subject::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();

        int pageIndex = Math.max(0, page);
        int pageSize = size <= 0 ? 20 : size;
        int from = pageIndex * pageSize;
        int to = Math.min(from + pageSize, all.size());
        List<Subject> pageItems = from >= all.size() ? List.of() : all.subList(from, to);

        List<SubjectListItem> items = pageItems.stream()
                .map(s -> toListItem(s, user))
                .toList();

        return new SubjectPageResponse(items, pageIndex, pageSize, all.size());
    }

    @Transactional
    public SubjectListItem create(User user, CreateSubjectRequest req) {
        if (req == null) throw new BadRequestException("Request is required");
        if (req.scope() == null) throw new BadRequestException("scope is required");

        String name = req.name() == null ? null : req.name().trim();
        if (name == null || name.isBlank()) throw new BadRequestException("Subject name is required");

        Subject subject = new Subject();
        subject.setName(name);
        subject.setScope(req.scope());

        if (req.scope() == SubjectScope.PERSONAL) {
            if (req.familyId() != null) {
                throw new BadRequestException("familyId must be null when scope is PERSONAL");
            }
            ensureUniquePersonal(user.getId(), name, null);
            subject.setOwner(user);
            subject.setFamily(null);
        } else {
            if (req.familyId() == null) throw new BadRequestException("familyId is required when scope is FAMILY");
            Family family = requireFamilyHead(req.familyId(), user);
            ensureUniqueFamily(family.getId(), name, null);
            subject.setFamily(family);
            subject.setOwner(null);
        }

        subject = subjectRepository.save(subject);
        return toListItem(subject, user);
    }

    @Transactional
    public SubjectListItem rename(User user, UUID subjectId, UpdateSubjectRequest req) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        assertCanManageSubject(subject, user);

        String name = req.name() == null ? null : req.name().trim();
        if (name == null || name.isBlank()) throw new BadRequestException("Subject name is required");

        if (subject.getScope() == SubjectScope.PERSONAL) {
            ensureUniquePersonal(user.getId(), name, subject.getId());
        } else {
            if (subject.getFamily() == null) throw new BadRequestException("Family subject is missing familyId");
            ensureUniqueFamily(subject.getFamily().getId(), name, subject.getId());
        }

        subject.setName(name);
        subject = subjectRepository.save(subject);
        return toListItem(subject, user);
    }

    @Transactional
    public void delete(User user, UUID subjectId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Subject not found"));

        assertCanManageSubject(subject, user);

        if (documentRepository.existsBySubject_Id(subjectId)) {
            throw new ResourceAlreadyExistsException("Cannot delete subject with documents. Move documents out first.");
        }

        subjectRepository.delete(subject);
    }

    public Subject requireSubjectForDocument(UUID subjectId, User user, SubjectScope expectedScope, Long expectedFamilyId) {
        Subject subject = subjectRepository.findById(subjectId)
                .orElseThrow(() -> new BadRequestException("Invalid subjectId"));

        if (subject.getScope() != expectedScope) {
            throw new BadRequestException("Subject scope does not match document scope");
        }

        if (expectedScope == SubjectScope.PERSONAL) {
            if (subject.getOwner() == null || subject.getOwner().getId() == null) {
                throw new BadRequestException("Subject owner is missing");
            }
            if (!subject.getOwner().getId().equals(user.getId())) {
                throw new ForbiddenException("Not allowed to use this subject");
            }
            return subject;
        }

        // FAMILY
        if (expectedFamilyId == null) {
            throw new BadRequestException("familyId is required for FAMILY subject assignment");
        }
        if (subject.getFamily() == null || subject.getFamily().getId() == null) {
            throw new BadRequestException("Subject family is missing");
        }
        if (!subject.getFamily().getId().equals(expectedFamilyId)) {
            throw new BadRequestException("Subject does not belong to the selected family");
        }

        return subject;
    }

    private SubjectListItem toListItem(Subject subject, User currentUser) {
        long documentCount;
        if (subject.getScope() == SubjectScope.PERSONAL) {
            Long ownerId = subject.getOwner() != null ? subject.getOwner().getId() : null;
            if (ownerId == null) {
                documentCount = 0;
            } else {
                documentCount = documentRepository.countByOwnerIdAndStatusAndVisibilityInAndSubject_Id(
                        ownerId, DocumentStatus.ACTIVE, PERSONAL_VISIBILITIES, subject.getId());
            }
        } else {
            Long familyId = subject.getFamily() != null ? subject.getFamily().getId() : null;
            if (familyId == null) {
                documentCount = 0;
            } else {
                documentCount = documentRepository.countByFamilyIdAndStatusAndVisibilityAndSubject_Id(
                        familyId, DocumentStatus.ACTIVE, DocumentVisibility.FAMILY, subject.getId());
            }
        }

        return new SubjectListItem(
                subject.getId(),
                subject.getName(),
                subject.getScope(),
                subject.getFamily() != null ? subject.getFamily().getPublicId() : null,
                subject.getOwner() != null ? subject.getOwner().getId() : null,
                documentCount,
                subject.getCreatedAt(),
                subject.getUpdatedAt()
        );
    }

    private Family requireFamilyMembership(UUID familyPublicId, User user) {
        Family family = familyRepository.findByPublicId(familyPublicId)
                .orElseThrow(() -> new BadRequestException("Family not found"));
        boolean member = familyMemberRepository.findByFamilyIdAndUserIdAndActiveTrue(family.getId(), user.getId()).isPresent();
        if (!member) {
            throw new ForbiddenException("Not a member of this family");
        }
        return family;
    }

    private Family requireFamilyHead(UUID familyPublicId, User user) {
        Family family = familyRepository.findByPublicId(familyPublicId)
                .orElseThrow(() -> new BadRequestException("Family not found"));

        boolean head = familyMemberRepository
                .existsByFamilyIdAndUserIdAndActiveTrueAndRoleIn(
                        family.getId(),
                        user.getId(),
                        List.of(FamilyRole.HEAD)
                );
        if (!head) {
            throw new ForbiddenException("Only family head can manage subjects");
        }
        return family;
    }

    private void assertCanManageSubject(Subject subject, User user) {
        if (subject.getScope() == SubjectScope.PERSONAL) {
            if (subject.getOwner() == null || subject.getOwner().getId() == null) {
                throw new BadRequestException("Subject owner is missing");
            }
            if (!subject.getOwner().getId().equals(user.getId())) {
                throw new ForbiddenException("Not allowed to manage this subject");
            }
            return;
        }

        if (subject.getFamily() == null || subject.getFamily().getPublicId() == null) {
            throw new BadRequestException("Subject family is missing");
        }
        requireFamilyHead(subject.getFamily().getPublicId(), user);
    }

    private void ensureUniquePersonal(Long ownerId, String name, UUID excludeId) {
        subjectRepository.findByOwnerIdAndScopeAndNameIgnoreCase(ownerId, SubjectScope.PERSONAL, name)
                .filter(s -> excludeId == null || !s.getId().equals(excludeId))
                .ifPresent(s -> {
                    throw new ResourceAlreadyExistsException("Subject with this name already exists");
                });
    }

    private void ensureUniqueFamily(Long familyId, String name, UUID excludeId) {
        subjectRepository.findByFamilyIdAndScopeAndNameIgnoreCase(familyId, SubjectScope.FAMILY, name)
                .filter(s -> excludeId == null || !s.getId().equals(excludeId))
                .ifPresent(s -> {
                    throw new ResourceAlreadyExistsException("Subject with this name already exists");
                });
    }
}
