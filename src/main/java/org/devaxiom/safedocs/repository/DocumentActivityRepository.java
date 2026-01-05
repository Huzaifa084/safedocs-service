package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.enums.DocumentActivityAction;
import org.devaxiom.safedocs.model.DocumentActivity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentActivityRepository extends JpaRepository<DocumentActivity, Long> {

    List<DocumentActivity> findTop50ByDocument_PublicIdOrderByCreatedDateDesc(java.util.UUID documentPublicId);

    long countByDocument_IdAndAction(Long documentId, DocumentActivityAction action);
}
