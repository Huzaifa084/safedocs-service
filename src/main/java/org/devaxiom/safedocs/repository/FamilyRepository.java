package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.model.Family;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FamilyRepository extends JpaRepository<Family, Long> {
    Optional<Family> findByPublicId(UUID publicId);
}
