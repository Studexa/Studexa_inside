package com.studexa.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.game.dto.AwardResult;
import com.studexa.game.dto.BadgeDto;
import com.studexa.game.dto.GameStateDto;
import com.studexa.game.dto.XpEventDto;
import com.studexa.user.User;
import com.studexa.user.UserRepository;

/**
 * Tests for the secure game engine. Runs against {@code studexa_test} only,
 * guarded by {@code TestDatabaseGuard}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GameServiceTest {

    @Autowired
    private GameService gameService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GameStateRepository gameStateRepository;

    private UUID userId;

    @BeforeEach
    void setUp() {
        User user = userRepository.save(User.withPassword("test-" + UUID.randomUUID() + "@example.com", "hash"));
        userId = user.getId();
    }

    @Test
    void xpAwardedIncreasesTotalAndLevel() {
        gameService.awardXp(userId, "review_card", 3, UUID.randomUUID(), Map.of());
        GameStateDto state = gameService.getState(userId);
        assertThat(state.xpTotal()).isEqualTo(3);
        assertThat(state.level()).isEqualTo(1);

        gameService.awardXp(userId, "review_card", 97, UUID.randomUUID(), Map.of());
        state = gameService.getState(userId);
        assertThat(state.xpTotal()).isEqualTo(100);
        assertThat(state.level()).isEqualTo(2);
        assertThat(state.xpInLevel()).isEqualTo(0);
    }

    @Test
    void xpToNextLevelFormula() {
        GameProperties props = new GameProperties(
                3, 2, 20, 5, 5, 25, 25, 8, 2, 5, 20, 30, 100, 10, 1, 5,
                0.10, 0.50, 600, 100, 50, 20, 50, 2, 1, 10, List.of(), 800, 10, 7, 1);
        assertThat(props.xpToNextLevel(1)).isEqualTo(100);
        assertThat(props.xpToNextLevel(2)).isEqualTo(150);
        assertThat(props.xpToNextLevel(3)).isEqualTo(200);
        assertThat(props.xpToNextLevel(10)).isEqualTo(550);
    }

    @Test
    void dailyXpCapStopsFurtherXp() {
        int cap = 600;
        gameService.awardXp(userId, "review_card", cap, UUID.randomUUID(), Map.of());
        GameStateDto state = gameService.getState(userId);
        assertThat(state.dailyXpToday()).isEqualTo(cap);

        AwardResult result = gameService.awardXp(userId, "review_card", 100, UUID.randomUUID(), Map.of());
        assertThat(result.dailyCapReached()).isTrue();
        assertThat(result.xpAwarded()).isZero();
        state = gameService.getState(userId);
        assertThat(state.dailyXpToday()).isEqualTo(cap);
    }

    @Test
    void perCardPerDayReviewLimit() {
        UUID cardReference = UUID.randomUUID();
        gameService.awardXp(userId, "review_card", 3, cardReference, Map.of("reviewsForCardToday", 0));
        GameStateDto afterFirst = gameService.getState(userId);
        assertThat(afterFirst.xpTotal()).isEqualTo(3);

        gameService.awardXp(userId, "review_card", 3, UUID.randomUUID(),
                Map.of("reviewsForCardToday", 1));
        GameStateDto afterSecond = gameService.getState(userId);
        assertThat(afterSecond.xpTotal()).isEqualTo(3); // unchanged
    }

    @Test
    void idempotentXpAward() {
        UUID reference = UUID.randomUUID();
        gameService.awardXp(userId, "review_card", 3, reference, Map.of());
        GameStateDto afterFirst = gameService.getState(userId);
        assertThat(afterFirst.xpTotal()).isEqualTo(3);

        gameService.awardXp(userId, "review_card", 3, reference, Map.of());
        GameStateDto afterSecond = gameService.getState(userId);
        assertThat(afterSecond.xpTotal()).isEqualTo(3);
    }

    @Test
    void concurrencyTestXpCapRespected() throws Exception {
        int cap = 600;
        int numThreads = 20;
        int xpPerRequest = 50;

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads);
        AtomicInteger successes = new AtomicInteger(0);

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    AwardResult result = gameService.awardXp(userId, "review_card",
                            xpPerRequest, UUID.randomUUID(), Map.of());
                    if (result.awarded()) {
                        successes.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        GameStateDto state = gameService.getState(userId);
        assertThat(state.dailyXpToday()).isLessThanOrEqualTo(cap);
        assertThat(state.xpTotal()).isLessThanOrEqualTo(cap);
    }

    @Test
    void consumeStreakFreezeWithoutFreezesFails() {
        gameService.awardXp(userId, "review_card", 600, UUID.randomUUID(), Map.of());
        assertThatThrownBy(() -> gameService.consumeStreakFreeze(userId))
                .isInstanceOf(com.studexa.common.ApiException.class);
    }

    @Test
    void badgesAreListed() {
        List<BadgeDto> badges = gameService.getBadges(userId);
        assertThat(badges).isNotEmpty();
        assertThat(badges).allMatch(b -> !b.earned());
    }

    @Test
    void xpHistoryIsEmptyInitially() {
        List<XpEventDto> history = gameService.getXpHistory(userId, 20);
        assertThat(history).isEmpty();
    }
}
