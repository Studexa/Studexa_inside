package com.studexa.notes;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByUserIdOrderByUpdatedAtDesc(UUID userId);

    @Query("""
            select count(n) from Note n
            where n.userId = :userId
            """)
    long countByUserId(@Param("userId") UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);
}
