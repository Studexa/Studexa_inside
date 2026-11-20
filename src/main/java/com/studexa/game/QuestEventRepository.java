package com.studexa.game;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestEventRepository extends JpaRepository<QuestEvent, UUID> {

    boolean existsByUserIdAndQuestIdAndSourceAndReferenceId(
            UUID userId, UUID questId, String source, UUID referenceId);
}
