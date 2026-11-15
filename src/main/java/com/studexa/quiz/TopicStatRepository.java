package com.studexa.quiz;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicStatRepository extends JpaRepository<TopicStat, TopicStatId> {

    Optional<TopicStat> findByUserIdAndSubjectAndTopic(UUID userId, String subject, String topic);

    List<TopicStat> findByUserIdOrderByWeaknessScoreDesc(UUID userId);
}
