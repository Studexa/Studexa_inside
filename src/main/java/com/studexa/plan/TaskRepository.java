package com.studexa.plan;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByUserIdOrderByTaskDateAsc(UUID userId);

    List<Task> findByUserIdAndTaskDateOrderByTaskDateAsc(UUID userId, LocalDate taskDate);
}
