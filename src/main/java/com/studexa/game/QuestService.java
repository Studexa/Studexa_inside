package com.studexa.game;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.game.dto.QuestDto;
import com.studexa.user.ProfileRepository;

/**
 * Quest service. Creates daily and weekly quests lazily on the first request
 * of the student's local day (Section 7.7). Updates progress from study
 * events and awards rewards through {@link GameService}.
 *
 * <p>Quest creation is guarded by a unique DB constraint on
 * {@code (user_id, quest_date, code)} so parallel first requests never
 * create duplicate quests (BUG-D09).
 */
@Service
public class QuestService {

    private final QuestRepository questRepository;
    private final QuestEventRepository questEventRepository;
    private final GameService gameService;
    private final ProfileRepository profileRepository;
    private final Clock clock;

    // In-flight quest creation locks per user, to avoid duplicate DB writes
    // under concurrent first-request-of-the-day load.
    private static final Map<UUID, Object> creationLocks = new ConcurrentHashMap<>();

    public QuestService(QuestRepository questRepository,
            QuestEventRepository questEventRepository, GameService gameService,
            ProfileRepository profileRepository, Clock clock) {
        this.questRepository = questRepository;
        this.questEventRepository = questEventRepository;
        this.gameService = gameService;
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    /**
     * Returns today's quests for the user, creating them lazily if they don't
     * exist yet. Daily quests are created on the student's local date;
     * weekly quests are created on the Monday that starts the current week.
     */
    @Transactional
    public List<QuestDto> getToday(UUID userId) {
        ZoneId zoneId = zoneIdFor(userId);
        LocalDate today = clock.instant().atZone(zoneId).toLocalDate();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);

        List<Quest> existing = questRepository.findByUserIdAndQuestDateOrderByCode(userId, today);

        boolean needsDaily = existing.stream().noneMatch(q -> q.getQuestDate().equals(today) && q.getKind() == QuestKind.DAILY);
        boolean needsWeekly = existing.stream().noneMatch(q -> q.getQuestDate().equals(weekStart) && q.getKind() == QuestKind.WEEKLY);

        if (needsDaily || needsWeekly) {
            Object lock = creationLocks.computeIfAbsent(userId, k -> new Object());
            synchronized (lock) {
                // Re-check after acquiring the lock (another thread may have created them).
                existing = questRepository.findByUserIdAndQuestDateOrderByCode(userId, today);
                needsDaily = existing.stream().noneMatch(q -> q.getQuestDate().equals(today) && q.getKind() == QuestKind.DAILY);
                needsWeekly = existing.stream().noneMatch(q -> q.getQuestDate().equals(weekStart) && q.getKind() == QuestKind.WEEKLY);

                if (needsDaily) {
                    createDailyQuests(userId, today);
                }
                if (needsWeekly) {
                    createWeeklyQuests(userId, weekStart);
                }
                existing = questRepository.findByUserIdAndQuestDateOrderByCode(userId, today);
            }
        }

        return existing.stream().map(this::toDto).toList();
    }

    /**
     * Update progress on a quest by code. Idempotent via {@code referenceId}.
     * Awards XP and coins through {@link GameService} when a quest completes.
     * <p>
     * The amount is derived server-side from the source; the client must not
     * send it. Each source maps to a fixed increment of 1 per call.
     */
    @Transactional
    public void addProgress(UUID userId, String code, String source, UUID referenceId) {
        int amount = amountForSource(source);
        if (amount <= 0) {
            return;
        }

        ZoneId zoneId = zoneIdFor(userId);
        LocalDate today = clock.instant().atZone(zoneId).toLocalDate();

        Quest quest = questRepository.findByUserIdAndQuestDateAndCodeAndCompletedFalse(userId, today, code)
                .orElse(null);

        if (quest == null) {
            return; // no active quest with this code today
        }

        // Idempotency: skip if this event was already applied.
        if (referenceId != null && alreadyApplied(userId, quest.getId(), source, referenceId)) {
            return;
        }

        int newProgress = Math.min(quest.getTarget(), quest.getProgress() + amount);
        quest.setProgress(newProgress);
        questRepository.save(quest);

        if (newProgress >= quest.getTarget() && !quest.isCompleted()) {
            quest.setCompleted(true);
            questRepository.save(quest);

            // Award reward through the secure game engine. XP goes through
            // awardXp (daily cap applies); coins are credited separately so a
            // coin reward is never mistaken for XP.
            gameService.awardXp(userId, "quest_" + code, quest.getRewardXp(), referenceId, Map.of("goalMet", true));
            gameService.awardCoins(userId, quest.getRewardCoins());
        }

        recordQuestEvent(userId, quest.getId(), source, referenceId);
    }

    /**
     * Derives the quest progress increment from the event source. The client
     * must not be able to inflate progress, so each source maps to exactly 1.
     */
    private static int amountForSource(String source) {
        return switch (source) {
            case "review_card", "focus_session", "note_create", "mini_game" -> 1;
            default -> 0;
        };
    }

    /* ---------------------------------------------------------------- */

    private void createDailyQuests(UUID userId, LocalDate date) {
        // At least one easy quest every day; rotate the other two.
        List<QuestDef> defs = dailyQuestDefs(date);
        Instant now = clock.instant();
        for (QuestDef def : defs) {
            if (!questRepository.existsByUserIdAndQuestDateAndCode(userId, date, def.code())) {
                Quest quest = new Quest(
                        UUID.randomUUID(), userId, QuestKind.DAILY,
                        def.code(), def.target(), def.rewardXp(), def.rewardCoins(), date, now);
                questRepository.save(quest);
            }
        }
    }

    private void createWeeklyQuests(UUID userId, LocalDate weekStart) {
        List<QuestDef> defs = weeklyQuestDefs(weekStart);
        Instant now = clock.instant();
        for (QuestDef def : defs) {
            if (!questRepository.existsByUserIdAndQuestDateAndCode(userId, weekStart, def.code())) {
                Quest quest = new Quest(
                        UUID.randomUUID(), userId, QuestKind.WEEKLY,
                        def.code(), def.target(), def.rewardXp(), def.rewardCoins(), weekStart, now);
                questRepository.save(quest);
            }
        }
    }

    private List<QuestDef> dailyQuestDefs(LocalDate date) {
        // Warm-up is always the easy daily quest.
        // Rotate builder/focus based on day of week for variety.
        int day = date.getDayOfWeek().getValue();
        String second = (day % 2 == 0) ? "builder" : "focus";
        String third = (day % 3 == 0) ? "hotStreak" : "noteTaker";

        return List.of(
                new QuestDef("warmup", 10, 15, 5),
                new QuestDef(second, second.equals("builder") ? 3 : 1,
                        second.equals("builder") ? 15 : 25,
                        second.equals("builder") ? 5 : 8),
                new QuestDef(third, third.equals("hotStreak") ? 5 : 1,
                        third.equals("hotStreak") ? 15 : 15,
                        third.equals("hotStreak") ? 5 : 5));
    }

    private List<QuestDef> weeklyQuestDefs(LocalDate weekStart) {
        return List.of(
                new QuestDef("consistency", 5, 100, 40),
                new QuestDef("review150", 150, 100, 40));
    }

    private ZoneId zoneIdFor(UUID userId) {
        return profileRepository.findById(userId)
                .map(p -> {
                    try {
                        return ZoneId.of(p.getTimezone());
                    } catch (Exception e) {
                        return ZoneId.of("Asia/Phnom_Penh");
                    }
                })
                .orElse(ZoneId.of("Asia/Phnom_Penh"));
    }

    private boolean alreadyApplied(UUID userId, UUID questId, String source, UUID referenceId) {
        if (referenceId == null) {
            return false;
        }
        return questEventRepository.existsByUserIdAndQuestIdAndSourceAndReferenceId(
                userId, questId, source, referenceId);
    }

    private void recordQuestEvent(UUID userId, UUID questId, String source, UUID referenceId) {
        if (referenceId == null) {
            return;
        }
        QuestEvent event = QuestEvent.create(
                UUID.randomUUID(), userId, questId, source, referenceId, clock.instant());
        questEventRepository.save(event);
    }

    private QuestDto toDto(Quest quest) {
        return new QuestDto(
                quest.getId(),
                quest.getKind().name().toLowerCase(),
                quest.getCode(),
                "quests." + quest.getCode(),
                quest.getTarget(),
                quest.getProgress(),
                quest.getRewardXp(),
                quest.getRewardCoins(),
                quest.getQuestDate(),
                quest.isCompleted());
    }

    private record QuestDef(String code, int target, int rewardXp, int rewardCoins) {
    }
}
