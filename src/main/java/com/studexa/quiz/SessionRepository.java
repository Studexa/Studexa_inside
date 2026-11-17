package com.studexa.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    Optional<Session> findByRoomCode(String roomCode);

    List<Session> findByHostId(UUID hostId);

    List<Session> findByStatus(Session.Status status);
}
