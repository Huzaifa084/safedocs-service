package org.devaxiom.safedocs.repository;

import org.devaxiom.safedocs.model.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Modifying
    @Transactional
    @Query("update User u set u.tokenVersion = u.tokenVersion + 1 where u.id = :id")
    void incrementTokenVersion(Long id);

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findByUsername(String username);

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findByEmail(String normalized);

    @EntityGraph(attributePaths = {"role"})
    Optional<User> findById(Long id);
}
