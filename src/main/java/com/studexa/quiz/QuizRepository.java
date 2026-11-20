package com.studexa.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {

    List<Quiz> findByOwnerId(UUID ownerId);

    List<Quiz> findBySubject(String subject);

    Optional<Quiz> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query(value = "select exists (select 1 from slide_decks where quiz_id = :quizId)", nativeQuery = true)
    boolean isSlideDeckQuiz(@Param("quizId") UUID quizId);

    @Query("SELECT q FROM Quiz q WHERE q.visibility = 'PUBLIC'")
    List<Quiz> findPublicQuizzes();
}
