package com.studexa.game;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {

    List<XpEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, PageRequest pageable);

    boolean existsByUserIdAndSourceAndReferenceId(UUID userId, String source, UUID referenceId);
}
