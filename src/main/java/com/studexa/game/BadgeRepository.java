package com.studexa.game;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BadgeRepository extends JpaRepository<Badge, String> {

    Optional<Badge> findByCode(String code);

    List<Badge> findAllByOrderByCode();
}
