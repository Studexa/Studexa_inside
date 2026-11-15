package com.studexa.game;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestEventRepository extends JpaRepository<QuestEvent, UUID> {

    @Query("""
            select count(qe) from QuestEvent qe
            where qe.userId = :userId and qe.questId = :questId
              and qe.source = :source and qe.referenceId = :referenceId
            """)
    boolean existsByUserIdAndQuestIdAndSourceAndReferenceId(
            @Param("userId") UUID userId,
            @Param("questId") UUID questId,
            @Param("source") String source,
            @Param("referenceId") UUID referenceId);
}
