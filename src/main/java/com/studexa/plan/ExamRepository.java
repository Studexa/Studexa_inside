package com.studexa.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExamRepository extends JpaRepository<Exam, UUID> {

    List<Exam> findByUserIdOrderByExamDateAsc(UUID userId);

    @Query("""
            select count(e) from Exam e
            where e.userId = :userId
            """)
    long countByUserId(@Param("userId") UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    List<Exam> findByUserIdAndExamDateBetweenOrderByExamDateAsc(UUID userId, LocalDate start, LocalDate end);
}
