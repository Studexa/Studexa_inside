package com.studexa.game;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserBadgeRepository extends JpaRepository<UserBadge, UserBadgeId> {

    boolean existsById_UserIdAndId_BadgeCode(UUID userId, String badgeCode);

    List<UserBadge> findById_UserId(UUID userId);

    @Query("select count(ub) from UserBadge ub where ub.id.userId = :userId")
    long countByUserId(@Param("userId") UUID userId);
}
