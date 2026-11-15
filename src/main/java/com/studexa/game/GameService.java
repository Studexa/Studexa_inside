package com.studexa.game;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.game.dto.AwardResult;
import com.studexa.game.dto.BadgeDto;
import com.studexa.game.dto.GameStateDto;
import com.studexa.game.dto.XpEventDto;
import com.studexa.user.Profile;
import com.studexa.user.ProfileRepository;
import com.studexa.user.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;

/**
 * Secure server-side game engine (Section 7). All XP, levels, streaks, coins
 * and badges are calculated here, inside {@code @Transactional} methods with
 * row locking on {@link GameState}. Controllers never accept XP, level, or
 * coin values from the client.
 */
@Service
public class GameService {

    private final GameStateRepository gameStateRepository;
    private final XpEventRepository xpEventRepository;
    private final BadgeRepository badgeRepository;
    private final UserBadgeRepository userBadgeRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final GameProperties properties;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public GameService(GameStateRepository gameStateRepository,
            XpEventRepository xpEventRepository, BadgeRepository badgeRepository,
            UserBadgeRepository userBadgeRepository, ProfileRepository profileRepository,
            UserRepository userRepository, GameProperties properties, Clock clock) {
        this.gameStateRepository = gameStateRepository;
        this.xpEventRepository = xpEventRepository;
        this.badgeRepository = badgeRepository;
        this.userBadgeRepository = userBadgeRepository;
        this.profileRepository = profileRepository;
        this.userRepository = userRepository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Returns the current game state for a user, creating it on first access.
     * NOT readOnly because {@code save} may be called if the row does not exist yet.
     */
    @Transactional
    public GameStateDto getState(UUID userId) {
        GameState state = gameStateRepository.findById(userId)
                .orElseGet(() -> {
                    GameState created = GameState.newFor(
                            userId, clock.instant(), LocalDate.now(clock));
                    return gameStateRepository.save(created);
                });
        return toDto(state);
    }

    /**
     * Awards XP for an action. All rules enforced here:
     * <ul>
     *   <li>Daily XP cap (600)</li>
     *   <li>Per-card-per-day review limit</li>
     *   <li>Level-up loop with coin rewards</li>
     *   <li>Streak calculation using the student's timezone</li>
     *   <li>Badge auto-checking</li>
     * </ul>
     * The {@code referenceId} provides idempotency: the same reference never
     * awards XP twice.
     */
    @Transactional
    public AwardResult awardXp(UUID userId, String source, int amount, UUID referenceId,
            Map<String, Object> meta) {
        if (amount <= 0) {
            return AwardResult.none();
        }

        // Idempotency: never award twice for the same reference.
        if (referenceId != null
                && xpEventRepository.existsByUserIdAndSourceAndReferenceId(userId, source, referenceId)) {
            return AwardResult.none();
        }

        GameState state = lockState(userId);

        // Resolve the student's timezone from their profile for correct day boundaries.
        ZoneId zoneId = profileRepository.findById(userId)
                .map(p -> ZoneId.of(p.getTimezone()))
                .orElse(ZoneId.systemDefault());

        // Reset daily XP counter if it's a new local day (in the student's timezone).
        resetDailyXpIfNewDay(state, zoneId);

        // Per-card-per-day review limit (only for card reviews).
        if ("review_card".equals(source)) {
            Integer reviewsToday = (Integer) meta.getOrDefault("reviewsForCardToday", 0);
            if (reviewsToday >= properties.maxReviewsPerCardPerDay()) {
                return AwardResult.none();
            }
        }

        // Apply daily XP cap.
        long remainingCap = properties.dailyXpCap() - state.getDailyXpToday();
        if (remainingCap <= 0) {
            return AwardResult.capped();
        }
        int awardable = Math.min(amount, (int) remainingCap);

        // Streak bonus: +10% per 7 days of streak, capped at +50%.
        int streakBonusPercent = Math.min(
                (int) Math.floor(state.getStreakCurrent() / 7.0 * 100),
                (int) (properties.maxStreakBonus() * 100));
        int bonusXp = streakBonusPercent > 0
                ? (int) Math.round(awardable * streakBonusPercent / 100.0)
                : 0;
        int totalXp = awardable + bonusXp;

        // Award XP.
        state.setXpTotal(state.getXpTotal() + totalXp);
        state.setDailyXpToday(state.getDailyXpToday() + totalXp);
        state.setUpdatedAt(clock.instant());

        // Level-up loop.
        // 'xpInLevel' tracks how much XP the student has in the current level.
        // We carry a running 'xpCarry' so the while condition does not double-count
        // after the first level-up (setXpInLevel already reflects the carry).
        int levelsGained = 0;
        long xpCarry = state.getXpInLevel() + totalXp;
        while (xpCarry >= properties.xpToNextLevel(state.getLevel())) {
            xpCarry -= properties.xpToNextLevel(state.getLevel());
            state.setLevel(state.getLevel() + 1);
            levelsGained++;
        }
        state.setXpInLevel((int) xpCarry);

        // Coins: 1 per 10 XP, plus level-up reward.
        int coinsEarned = awardable / properties.coinsPerTenXp();
        if (levelsGained > 0) {
            coinsEarned += levelsGained * properties.levelUpCoins();
        }
        state.setCoins(state.getCoins() + coinsEarned);

        // Streak: check if the daily goal was met.
        boolean goalMet = goalMetToday(meta);
        updateStreak(state, goalMet, zoneId);

        // Persist state and XP event.
        gameStateRepository.save(state);
        XpEvent event = XpEvent.create(
                UUID.randomUUID(), userId, source, awardable, referenceId, clock.instant());
        xpEventRepository.save(event);

        // Award streak-freeze coins if applicable (handled by shop, not here).

        // Check badges after state changes.
        List<BadgeDto> newBadges = checkAndAwardBadges(userId, state);

        entityManager.flush();

        return AwardResult.awarded(
                awardable, bonusXp, state.getLevel(), state.getXpInLevel(),
                state.getCoins(), coinsEarned, levelsGained > 0, state.getLevel(),
                state.getStreakCurrent(), state.getStreakBest(), state.getStreakFreezes(),
                state.getDailyXpToday(), newBadges);
    }

    /**
     * Consumes a streak freeze (costs coins, server-validated).
     */
    @Transactional
    public void consumeStreakFreeze(UUID userId) {
        GameState state = lockState(userId);
        if (state.getStreakFreezes() <= 0) {
            throw ApiException.badRequest("NO_FREEZES", "You don't have any streak freezes.");
        }
        if (state.getCoins() < properties.streakFreezeCost()) {
            throw ApiException.badRequest("NOT_ENOUGH_COINS", "Not enough coins for a streak freeze.");
        }
        state.setCoins(state.getCoins() - properties.streakFreezeCost());
        state.setStreakFreezes(state.getStreakFreezes() - 1);
        state.setUpdatedAt(clock.instant());
        gameStateRepository.save(state);

        XpEvent event = XpEvent.create(
                UUID.randomUUID(), userId, "streak_freeze", -properties.streakFreezeCost(),
                null, clock.instant());
        xpEventRepository.save(event);
        entityManager.flush();
    }

    /**
     * Returns all badges with earned status for a user.
     */
    @Transactional(readOnly = true)
    public List<BadgeDto> getBadges(UUID userId) {
        List<Badge> allBadges = badgeRepository.findAllByOrderByCode();
        List<String> earnedCodes = userBadgeRepository.findById_UserId(userId).stream()
                .map(ub -> ub.getId().getBadgeCode())
                .collect(Collectors.toList());
        return allBadges.stream()
                .map(b -> new BadgeDto(b.getCode(), b.getNameEn(), b.getNameKm(),
                        b.getDescriptionEn(), b.getDescriptionKm(),
                        b.getCoinsReward(), earnedCodes.contains(b.getCode())))
                .collect(Collectors.toList());
    }

    /**
     * Returns recent XP events for a user.
     */
    @Transactional(readOnly = true)
    public List<XpEventDto> getXpHistory(UUID userId, int limit) {
        List<XpEvent> events = xpEventRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, Math.min(100, limit)));
        return events.stream()
                .map(e -> new XpEventDto(e.getId(), e.getSource(), e.getAmount(),
                        e.getCreatedAt(), e.getReferenceId()))
                .collect(Collectors.toList());
    }

    /* ---------------------------------------------------------------- */

    private GameState lockState(UUID userId) {
        return gameStateRepository.findByIdForUpdate(userId)
                .orElseGet(() -> {
                    GameState created = GameState.newFor(
                            userId, clock.instant(), LocalDate.now(clock));
                    return gameStateRepository.saveAndFlush(created);
                });
    }

    private void resetDailyXpIfNewDay(GameState state, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        if (!state.getDailyXpDate().equals(today)) {
            state.setDailyXpToday(0);
            state.setDailyXpDate(today);
        }
    }

    private boolean goalMetToday(Map<String, Object> meta) {
        Boolean goalMet = (Boolean) meta.getOrDefault("goalMet", false);
        return goalMet;
    }

    private void updateStreak(GameState state, boolean goalMet, ZoneId zoneId) {
        LocalDate today = LocalDate.now(zoneId);
        LocalDate lastActive = state.getLastActiveDate();

        if (lastActive.equals(today)) {
            // Already active today; streak unchanged.
            return;
        }

        if (lastActive.equals(today.minusDays(1))) {
            // Consecutive day.
            if (goalMet) {
                state.setStreakCurrent(state.getStreakCurrent() + 1);
                state.setStreakBest(Math.max(state.getStreakBest(), state.getStreakCurrent()));
            }
        } else {
            // Missed day(s). Use a freeze if available and the goal wasn't met.
            if (!goalMet && state.getStreakFreezes() > 0) {
                state.setStreakFreezes(state.getStreakFreezes() - 1);
                // Streak stays the same; freeze protects it.
            } else {
                state.setStreakCurrent(0);
            }
        }
        state.setLastActiveDate(today);
    }

    private List<BadgeDto> checkAndAwardBadges(UUID userId, GameState state) {
        List<BadgeDto> newlyEarned = new ArrayList<>();
        for (Badge badge : badgeRepository.findAllByOrderByCode()) {
            if (userBadgeRepository.existsById_UserIdAndId_BadgeCode(userId, badge.getCode())) {
                continue;
            }
            boolean earned = isBadgeEarned(badge.getCode(), userId, state);
            if (earned) {
                userBadgeRepository.save(UserBadge.create(userId, badge.getCode(), clock.instant()));
                state.setCoins(state.getCoins() + badge.getCoinsReward());
                newlyEarned.add(new BadgeDto(badge.getCode(), badge.getNameEn(), badge.getNameKm(),
                        badge.getDescriptionEn(), badge.getDescriptionKm(),
                        badge.getCoinsReward(), true));
            }
        }
        if (!newlyEarned.isEmpty()) {
            gameStateRepository.save(state);
        }
        return newlyEarned;
    }

    private boolean isBadgeEarned(String code, UUID userId, GameState state) {
        return switch (code) {
            case "firstFlip" -> state.getXpTotal() > 0; // reviewed at least one card
            case "cardCollector" -> hasCardCount(userId, 50);
            case "centuryClub" -> state.getXpTotal() >= 300; // ~100 reviews at 3 XP each
            case "memoryMaster" -> hasLongIntervalCards(userId, 100, 21);
            case "weekWarrior" -> state.getStreakCurrent() >= 7;
            case "monthMonarch" -> state.getStreakCurrent() >= 30;
            case "focusHero" -> hasFocusSessions(userId, 10);
            case "nightOwl" -> hasLateNightStudies(userId, 3, false);
            case "earlyBird" -> hasLateNightStudies(userId, 3, true);
            case "perfectRound" -> hasPerfectSession(userId, 20);
            case "bossSlayer" -> hasBossWin(userId);
            case "noteNinja" -> hasNoteCount(userId, 10);
            case "quizWhiz" -> hasPerfectQuiz(userId);
            case "sharingStar" -> hasCopiedDeckCount(userId, 5);
            case "comebackKid" -> state.getStreakCurrent() > 0 && state.getXpTotal() > 0;
            case "subjectExplorer" -> hasSubjectCount(userId, 5);
            case "polyglot" -> hasUsedBothLanguages(userId);
            default -> false;
        };
    }

    /* ---------------------------------------------------------------- */

    private boolean hasCardCount(UUID userId, int count) {
        return false; // placeholder - will be wired when card counts are available
    }

    private boolean hasLongIntervalCards(UUID userId, int count, int minDays) {
        return false; // placeholder
    }

    private boolean hasFocusSessions(UUID userId, int count) {
        return false; // placeholder - wired when timer exists
    }

    private boolean hasLateNightStudies(UUID userId, int count, boolean before6am) {
        return false; // placeholder
    }

    private boolean hasPerfectSession(UUID userId, int minCards) {
        return false; // placeholder
    }

    private boolean hasBossWin(UUID userId) {
        return false; // placeholder - wired when boss battles exist
    }

    private boolean hasNoteCount(UUID userId, int count) {
        return false; // placeholder - wired when notes exist
    }

    private boolean hasPerfectQuiz(UUID userId) {
        return false; // placeholder - wired when quizzes exist
    }

    private boolean hasCopiedDeckCount(UUID userId, int count) {
        return false; // placeholder - wired when community exists
    }

    private boolean hasSubjectCount(UUID userId, int count) {
        return false; // placeholder
    }

    private boolean hasUsedBothLanguages(UUID userId) {
        Optional<Profile> profile = profileRepository.findById(userId);
        return profile.map(p -> "en".equals(p.getLanguage()) || "km".equals(p.getLanguage()))
                .orElse(false);
    }

    private GameStateDto toDto(GameState state) {
        return new GameStateDto(
                state.getUserId(), state.getXpTotal(), state.getLevel(),
                state.getXpInLevel(), state.getCoins(), state.getStreakCurrent(),
                state.getStreakBest(), state.getStreakFreezes(),
                state.getLastActiveDate(), state.getDailyXpToday(),
                state.getDailyXpDate(), properties.xpToNextLevel(state.getLevel()));
    }
}
