package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.enums.SubjectScope;
import org.devaxiom.safedocs.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    List<Subject> findByOwnerIdAndScope(Long ownerId, SubjectScope scope);

    List<Subject> findByFamilyIdAndScope(Long familyId, SubjectScope scope);

    Optional<Subject> findByOwnerIdAndScopeAndNameIgnoreCase(Long ownerId, SubjectScope scope, String name);

    Optional<Subject> findByFamilyIdAndScopeAndNameIgnoreCase(Long familyId, SubjectScope scope, String name);
}
