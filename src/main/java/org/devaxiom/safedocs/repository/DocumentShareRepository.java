package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.enums.DocumentShareStatus;
import org.devaxiom.safedocs.model.DocumentShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentShareRepository extends JpaRepository<DocumentShare, Long> {
    Optional<DocumentShare> findByIdAndDocumentId(Long id, Long documentId);

    Optional<DocumentShare> findByDocumentIdAndRecipientEmailAndStatus(Long documentId, String recipientEmail, DocumentShareStatus status);

    boolean existsByDocumentIdAndRecipientEmailAndStatus(Long documentId, String recipientEmail, DocumentShareStatus status);

    List<DocumentShare> findByDocumentIdAndStatus(Long documentId, DocumentShareStatus status);

    @Query("select ds from DocumentShare ds where lower(ds.recipientEmail) = lower(?1) and ds.status = ?2")
    List<DocumentShare> findByRecipientEmailAndStatus(String recipientEmail, DocumentShareStatus status);
}
