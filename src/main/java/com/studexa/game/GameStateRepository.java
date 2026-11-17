package com.studexa.game;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.LockModeType;

public interface GameStateRepository extends JpaRepository<GameState, UUID> {

    Optional<GameState> findById(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameState g where g.userId = :userId")
    Optional<GameState> findByIdForUpdate(@Param("userId") UUID userId);

    boolean existsById(UUID userId);

    @Transactional
    @Query("delete from GameState g where g.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}
