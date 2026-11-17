package com.studexa.game;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestRepository extends JpaRepository<Quest, UUID> {

    List<Quest> findByUserIdAndQuestDateOrderByCode(UUID userId, LocalDate questDate);

    @Query("""
            select count(q) from Quest q
            where q.userId = :userId and q.questDate = :questDate and q.completed = true
            """)
    long countCompletedByUserAndDate(@Param("userId") UUID userId, @Param("questDate") LocalDate questDate);

    boolean existsByUserIdAndQuestDateAndCode(UUID userId, LocalDate questDate, String code);

    List<Quest> findByUserIdAndCompletedFalseOrderByQuestDateDescCodeAsc(UUID userId);
}
