package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.enums.FamilyRole;
import org.devaxiom.safedocs.model.Family;
import org.devaxiom.safedocs.model.FamilyMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FamilyMemberRepository extends JpaRepository<FamilyMember, Long> {
    Optional<FamilyMember> findByFamilyIdAndUserIdAndActiveTrue(Long familyId, Long userId);

    Optional<FamilyMember> findByUserIdAndActiveTrue(Long userId);

    List<FamilyMember> findByFamilyIdAndRoleAndActiveTrue(Long familyId, FamilyRole role);
}
