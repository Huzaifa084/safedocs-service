package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.enums.PermissionJobAction;
import org.devaxiom.safedocs.enums.PermissionJobStatus;
import org.devaxiom.safedocs.model.PermissionJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionJobRepository extends JpaRepository<PermissionJob, Long> {

    Optional<PermissionJob> findByJobId(UUID jobId);

    List<PermissionJob> findByOwnerIdAndStatus(Long ownerId, PermissionJobStatus status);

    Optional<PermissionJob> findByDocumentPublicIdAndOwnerIdAndTargetUserEmailAndAction(
            UUID documentPublicId,
            Long ownerId,
            String targetUserEmail,
            PermissionJobAction action
    );
}
