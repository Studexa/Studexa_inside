package com.studexa.ai;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AiUsageRepository extends JpaRepository<AiUsage, AiUsageId> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO ai_usage (user_id, usage_date, calls)
            VALUES (:userId, :usageDate, 1)
            ON CONFLICT (user_id, usage_date)
            DO UPDATE SET calls = ai_usage.calls + 1
            WHERE ai_usage.calls < :limit
            """, nativeQuery = true)
    int tryIncrementUsage(@Param("userId") UUID userId,
                          @Param("usageDate") LocalDate usageDate,
                          @Param("limit") int limit);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE ai_usage SET calls = calls - 1
            WHERE user_id = :userId AND usage_date = :usageDate AND calls > 0
            """, nativeQuery = true)
    int decrementUsage(@Param("userId") UUID userId,
                       @Param("usageDate") LocalDate usageDate);
}
