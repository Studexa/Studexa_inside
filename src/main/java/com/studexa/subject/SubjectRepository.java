package com.studexa.subject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    long countByUserId(UUID userId);

    List<Subject> findAllByUserIdOrderByCreatedAtAsc(UUID userId);

    List<Subject> findAllByUserIdAndArchivedOrderByCreatedAtAsc(UUID userId, boolean archived);

    Optional<Subject> findByIdAndUserId(UUID id, UUID userId);

    List<Subject> findAllByUserIdAndIdIn(UUID userId, List<UUID> ids);
}
