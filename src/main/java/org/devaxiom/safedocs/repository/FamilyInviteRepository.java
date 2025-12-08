package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.enums.FamilyInviteStatus;
import org.devaxiom.safedocs.model.FamilyInvite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FamilyInviteRepository extends JpaRepository<FamilyInvite, Long> {
    Optional<FamilyInvite> findByPublicId(UUID publicId);

    boolean existsByFamilyIdAndEmailAndStatus(Long familyId, String email, FamilyInviteStatus status);

    List<FamilyInvite> findByFamilyIdAndStatus(Long familyId, FamilyInviteStatus status);

    List<FamilyInvite> findByFamilyId(Long familyId);

    List<FamilyInvite> findByEmailAndStatus(String email, FamilyInviteStatus status);
}
