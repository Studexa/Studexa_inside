package com.studexa.quiz;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRepository extends JpaRepository<Answer, UUID> {

    List<Answer> findBySessionPlayerId(UUID sessionPlayerId);

    List<Answer> findBySessionPlayerIdAndQuestionId(UUID sessionPlayerId, UUID questionId);
}
