package com.studexa.game;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PetRepository extends JpaRepository<Pet, UUID> {

    Optional<Pet> findById(UUID userId);

    boolean existsById(UUID userId);
}
