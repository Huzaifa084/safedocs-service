package org.devaxiom.safedocs.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.devaxiom.safedocs.dto.permission.PermissionJobItem;
import org.devaxiom.safedocs.dto.permission.PermissionJobResponse;
import org.devaxiom.safedocs.dto.permission.PermissionJobUpdateRequest;
import org.devaxiom.safedocs.enums.PermissionJobAction;
import org.devaxiom.safedocs.enums.PermissionJobStatus;
import org.devaxiom.safedocs.exception.BadRequestException;
import org.devaxiom.safedocs.exception.ResourceNotFoundException;
import org.devaxiom.safedocs.exception.UnauthorizedException;
import org.devaxiom.safedocs.model.Document;
import org.devaxiom.safedocs.model.Family;
import org.devaxiom.safedocs.model.PermissionJob;
import org.devaxiom.safedocs.model.User;
import org.devaxiom.safedocs.repository.DocumentRepository;
import org.devaxiom.safedocs.repository.PermissionJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermissionJobService {

    private final PermissionJobRepository permissionJobRepository;
    private final DocumentRepository documentRepository;

    public boolean enqueueJob(Document doc, User owner, String targetEmail, PermissionJobAction action, Family family) {
        if (doc == null || owner == null) return false;
        String normalizedEmail = normalizeEmail(targetEmail);
        if (normalizedEmail == null || normalizedEmail.isBlank()) return false;
        if (owner.getEmail() != null && normalizedEmail.equalsIgnoreCase(owner.getEmail())) return false;

        Optional<PermissionJob> existing = permissionJobRepository
                .findByDocumentPublicIdAndOwnerIdAndTargetUserEmailAndAction(
                        doc.getPublicId(),
                        owner.getId(),
                        normalizedEmail,
                        action
                );
        if (existing.isPresent()) {
            return false;
        }

        PermissionJob job = PermissionJob.builder()
                .documentPublicId(doc.getPublicId())
                .driveFileId(doc.getDriveFileId())
                .owner(owner)
                .targetUserEmail(normalizedEmail)
                .action(action)
                .family(family)
                .status(PermissionJobStatus.PENDING)
                .attempts(0)
                .build();
        permissionJobRepository.save(job);
        return true;
    }

    @Transactional
    public int enqueueJobs(List<PermissionJobItem> items, User owner) {
        int queued = 0;
        for (PermissionJobItem item : items) {
            Document doc = documentRepository.findByPublicId(item.documentPublicId())
                    .orElseThrow(() -> new ResourceNotFoundException("Document not found"));
            if (!doc.getOwner().getId().equals(owner.getId())) {
                throw new UnauthorizedException("Not allowed to create jobs for this document");
            }
            if (item.driveFileId() != null && !item.driveFileId().isBlank()
                    && !item.driveFileId().trim().equals(doc.getDriveFileId())) {
                throw new BadRequestException("driveFileId does not match document");
            }
            Family family = doc.getFamily();
            if (item.familyId() != null) {
                if (family == null) {
                    throw new BadRequestException("familyId not applicable for this document");
                }
                UUID docFamilyId = family.getPublicId();
                if (!item.familyId().equals(docFamilyId)) {
                    throw new BadRequestException("familyId does not match document");
                }
            }
            boolean created = enqueueJob(doc, owner, item.targetUserEmail(), item.action(), family);
            if (created) queued++;
        }
        return queued;
    }

    public List<PermissionJobResponse> listJobs(User owner, PermissionJobStatus status) {
        PermissionJobStatus resolved = status == null ? PermissionJobStatus.PENDING : status;
        return permissionJobRepository.findByOwnerIdAndStatus(owner.getId(), resolved)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public PermissionJobResponse updateJob(UUID jobId, PermissionJobUpdateRequest request, User owner) {
        PermissionJob job = permissionJobRepository.findByJobId(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getOwner().getId().equals(owner.getId())) {
            throw new UnauthorizedException("Not allowed to update this job");
        }
        if (job.getStatus() != PermissionJobStatus.PENDING) {
            throw new BadRequestException("Only PENDING jobs can be updated");
        }
        if (request.status() == PermissionJobStatus.PENDING) {
            throw new BadRequestException("Job status must be DONE or FAILED");
        }
        job.setStatus(request.status());
        if (request.attempts() != null) {
            job.setAttempts(Math.max(0, request.attempts()));
        }
        if (request.lastError() != null) {
            job.setLastError(request.lastError().trim());
        }
        permissionJobRepository.save(job);
        return toResponse(job);
    }

    private PermissionJobResponse toResponse(PermissionJob job) {
        return new PermissionJobResponse(
                job.getJobId(),
                job.getDocumentPublicId(),
                job.getDriveFileId(),
                job.getTargetUserEmail(),
                job.getAction(),
                job.getFamily() != null ? job.getFamily().getPublicId() : null,
                job.getStatus(),
                job.getAttempts(),
                job.getLastError(),
                job.getCreatedDate().orElse(null),
                job.getLastModifiedDate().orElse(null)
        );
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
