package com.studexa.quiz;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    List<Answer> findBySessionPlayerId(UUID sessionPlayerId);

    List<Answer> findBySessionPlayerIdAndQuestionId(UUID sessionPlayerId, UUID questionId);

    @Query("select count(a) from Answer a where a.questionId = :questionId "
            + "and a.sessionPlayerId in :playerIds")
    long countForQuestion(
            @Param("questionId") UUID questionId,
            @Param("playerIds") Collection<UUID> playerIds);
}
