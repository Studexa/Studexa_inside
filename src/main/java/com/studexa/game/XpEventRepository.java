package com.studexa.game;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface XpEventRepository extends JpaRepository<XpEvent, UUID> {

    List<XpEvent> findByUserIdOrderByCreatedAtDesc(UUID userId, PageRequest pageable);

    boolean existsByUserIdAndSourceAndReferenceId(UUID userId, String source, UUID referenceId);

    List<XpEvent> findByUserIdAndReferenceIdAndReversedFalse(UUID userId, UUID referenceId);

    @Modifying
    @Query("update XpEvent e set e.reversed = true where e.userId = :userId and e.referenceId = :referenceId and e.reversed = false")
    int markReversedByReferenceId(UUID userId, UUID referenceId);
}
