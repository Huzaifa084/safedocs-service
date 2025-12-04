package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.enums.DocumentStatus;
import org.devaxiom.safedocs.enums.DocumentVisibility;
import org.devaxiom.safedocs.model.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    Optional<Document> findByPublicId(UUID publicId);

    List<Document> findByOwnerIdAndVisibilityAndStatus(Long ownerId, DocumentVisibility visibility, DocumentStatus status, Sort sort);

    List<Document> findByFamilyIdAndStatus(Long familyId, DocumentStatus status, Sort sort);

    List<Document> findByOwnerIdAndStatus(Long ownerId, DocumentStatus status, Sort sort);

    List<Document> findByStatusAndExpiryDateBetween(DocumentStatus status, java.time.LocalDate from, java.time.LocalDate to);
}
