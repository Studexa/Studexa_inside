package com.studexa.deck;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.deck.dto.ReviewAnswerRequest;
import com.studexa.deck.dto.ReviewAnswerResponse;
import com.studexa.deck.dto.ReviewQueueCard;
import com.studexa.deck.dto.ReviewQueueResponse;
import com.studexa.deck.dto.ReviewUndoResponse;
import com.studexa.game.dto.AwardResult;
import com.studexa.game.GameService;
import com.studexa.game.GameProperties;
import com.studexa.srs.CardState;
import com.studexa.srs.CardStateType;
import com.studexa.srs.QueueCard;
import com.studexa.srs.Rating;
import com.studexa.srs.Sm2Scheduler;
import com.studexa.srs.StudyQueueBuilder;
import com.studexa.subject.Subject;
import com.studexa.subject.SubjectRepository;
import com.studexa.user.ProfileRepository;

@Service
public class ReviewService {

    private final DeckService deckService;
    private final CardRepository cardRepository;
    private final CardProgressRepository progressRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final SubjectRepository subjectRepository;
    private final ProfileRepository profileRepository;
    private final Sm2Scheduler scheduler;
    private final StudyQueueBuilder queueBuilder;
    private final Clock clock;
    private final GameService gameService;
    private final GameProperties gameProperties;

    public ReviewService(DeckService deckService, CardRepository cardRepository,
            CardProgressRepository progressRepository, ReviewLogRepository reviewLogRepository,
            SubjectRepository subjectRepository, ProfileRepository profileRepository, Clock clock,
            GameService gameService, GameProperties gameProperties) {
        this.deckService = deckService;
        this.cardRepository = cardRepository;
        this.progressRepository = progressRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.subjectRepository = subjectRepository;
        this.profileRepository = profileRepository;
        this.scheduler = new Sm2Scheduler();
        this.queueBuilder = new StudyQueueBuilder();
        this.clock = clock;
        this.gameService = gameService;
        this.gameProperties = gameProperties;
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse getQueue(UUID userId, UUID deckId) {
        if (deckId != null) {
            deckService.requireOwned(userId, deckId);
        }

        Instant now = clock.instant();

        List<Card> cards;
        if (deckId != null) {
            cards = cardRepository.findTop80ByDeckIdAndUserIdOrderByCreatedAtAsc(deckId, userId);
        } else {
            cards = cardRepository.findTop80ByUserIdOrderByCreatedAtDesc(userId);
        }

        if (cards.isEmpty()) {
            return new ReviewQueueResponse(List.of(), 0, 0, now);
        }

        Map<UUID, CardProgress> progress = progressByCard(userId, cards);

        // Load distinct decks for title/color/icon/subject info (batch-load to avoid N+1).
        List<UUID> deckIds = cards.stream().map(Card::getDeckId).distinct().toList();
        Map<UUID, Deck> decks = deckService.findByIds(userId, deckIds);

        List<QueueCard> queueCards = new ArrayList<>();
        for (Card card : cards) {
            CardProgress p = progress.get(card.getId());
            CardState state = toCardState(p);
            queueCards.add(new QueueCard(
                    card.getId(), state, card.isSuspended(), card.isStarred()));
        }

        Map<UUID, String> subjectNames = subjectNames(userId, new ArrayList<>(decks.values()));

        // Load archived subject IDs so we can exclude cards from archived subjects (BUG-C05).
        List<UUID> archivedSubjectIds = subjectIds(userId, decks.values()).stream()
                .filter(id -> id != null)
                .distinct()
                .filter(id -> subjectRepository.findByIdAndUserId(id, userId)
                        .map(Subject::isArchived).orElse(false))
                .toList();

        List<QueueCard> ordered = queueBuilder.build(queueCards, now);

        List<ReviewQueueCard> result = new ArrayList<>();
        for (QueueCard qc : ordered) {
            Card card = findCard(cards, qc.cardId());
            if (card == null) {
                continue;
            }
            CardProgress p = progress.get(card.getId());
            if (p == null) {
                continue;
            }
            Deck deck = decks.get(card.getDeckId());
            if (deck == null) {
                continue;
            }
            // Skip cards from archived subjects (BUG-C05).
            if (deck.getSubjectId() != null && archivedSubjectIds.contains(deck.getSubjectId())) {
                continue;
            }
            result.add(toQueueCard(card, p, deck, subjectNames.get(deck.getSubjectId()), qc.state()));
        }

        int remainingNew = countNew(cards, progress) - countNewInResult(result);
        int remainingReview = countReview(cards, progress) - countReviewInResult(result);

        return new ReviewQueueResponse(
                result,
                Math.max(0, remainingNew),
                Math.max(0, remainingReview),
                now);
    }

    @Transactional
    public ReviewAnswerResponse answer(UUID userId, UUID cardId, ReviewAnswerRequest request) {
        Card card = requireOwned(userId, cardId);
        deckService.requireOwned(userId, card.getDeckId());

        UUID clientReviewId = request.clientReviewId() != null
                ? UUID.fromString(request.clientReviewId())
                : UUID.randomUUID();

        if (reviewLogRepository.findByClientReviewId(clientReviewId).isPresent()) {
            throw ApiException.conflict("DUPLICATE_REVIEW", "This review was already recorded.");
        }

        CardProgress progress = progressRepository.findByCardIdAndUserId(cardId, userId)
                .orElseGet(() -> CardProgress.newFor(cardId, userId, clock.instant()));

        CardState current = toCardState(progress);
        Rating rating = toSRsRating(request.rating());
        Instant now = clock.instant();

        CardState next = scheduler.schedule(current, rating, now);
        Map<Rating, String> labels = scheduler.previewLabels(next);

        // Award XP through the secure server-side game engine.
        AwardResult award = gameService.awardXp(userId, "review_card", gameProperties.xpPerReview(), clientReviewId,
                Map.of("reviewsForCardToday", countReviewsForCardToday(userId, cardId)));

        // Bonus XP for Good or Easy ratings (once per card per day).
        int ratingBonusXp = 0;
        if (request.rating() == ReviewRating.GOOD || request.rating() == ReviewRating.EASY) {
            // Use the same clientReviewId so a retried answer never awards
            // the bonus XP twice (BUG-D01).
            AwardResult bonus = gameService.awardXp(userId, "review_card_bonus", 2,
                    clientReviewId, Map.of());
            ratingBonusXp = bonus.xpAwarded() + bonus.bonusXp();
        }

        int totalXp = award.xpAwarded() + award.bonusXp() + ratingBonusXp;

        ReviewLog log = ReviewLog.create(
                UUID.randomUUID(), clientReviewId, cardId, userId,
                request.rating().name(), now, request.msTaken(), totalXp,
                progress, current);

        progress.setEase(next.ease());
        progress.setIntervalDays(next.intervalDays());
        progress.setReps(next.reps());
        progress.setLapses(next.lapses());
        progress.setDueAt(next.dueAt());
        progress.setState(next.state().name());
        progress.setLastReviewedAt(now);

        progressRepository.save(progress);
        reviewLogRepository.save(log);

        return new ReviewAnswerResponse(
                card.getId(), card.getDeckId(), card.getType(),
                card.getFront(), card.getBack(), card.getExtra(), card.getTags(),
                next.state().name(), next.intervalDays(), next.reps(), next.lapses(),
                next.ease(), next.dueAt(), labels,
                totalXp, award.leveledUp(), award.newLevel(), null);
    }

    @Transactional
    public ReviewUndoResponse undo(UUID userId, UUID cardId) {
        Card card = requireOwned(userId, cardId);

        List<ReviewLog> logs = reviewLogRepository.findByCardIdAndUserIdOrderByReviewedAtDesc(cardId, userId);
        if (logs.isEmpty()) {
            throw ApiException.notFound("No review to undo for this card.");
        }

        ReviewLog last = logs.get(0);

        if (last.getReviewedAt() == null
                || last.getReviewedAt().plusSeconds(5).isBefore(clock.instant())) {
            throw ApiException.conflict("UNDO_EXPIRED", "The 5-second undo window has expired.");
        }

        CardProgress progress = progressRepository.findByCardIdAndUserId(cardId, userId)
                .orElseThrow(() -> ApiException.notFound("Card progress not found."));

        progress.setEase(last.getPreviousEase() != null ? last.getPreviousEase() : progress.getEase());
        progress.setIntervalDays(last.getPreviousIntervalDays() != null ? last.getPreviousIntervalDays() : progress.getIntervalDays());
        progress.setReps(last.getPreviousReps() != null ? last.getPreviousReps() : progress.getReps());
        progress.setLapses(last.getPreviousLapses() != null ? last.getPreviousLapses() : progress.getLapses());
        progress.setDueAt(last.getPreviousDueAt() != null ? last.getPreviousDueAt() : progress.getDueAt());
        progress.setState(last.getPreviousState() != null ? last.getPreviousState() : progress.getState());
        progress.setLastReviewedAt(last.getReviewedAt());

        progressRepository.save(progress);
        reviewLogRepository.delete(last);

        // Reverse the XP awarded for this review, if any.
        gameService.reverseXp(userId, last.getClientReviewId());

        CardState state = toCardState(progress);
        Map<Rating, String> labels = scheduler.previewLabels(state);

        return new ReviewUndoResponse(
                card.getId(), card.getDeckId(), card.getType(),
                card.getFront(), card.getBack(), card.getExtra(), card.getTags(),
                state.state().name(), state.intervalDays(), state.reps(), state.lapses(),
                state.ease(), state.dueAt(), labels, clock.instant());
    }

    private Card requireOwned(UUID userId, UUID cardId) {
        return cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> ApiException.notFound("Card not found."));
    }

    private Map<UUID, CardProgress> progressByCard(UUID userId, List<Card> cards) {
        if (cards.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = cards.stream().map(Card::getId).toList();
        return progressRepository.findAllByUserIdAndCardIdIn(userId, ids).stream()
                .collect(Collectors.toMap(CardProgress::getCardId, java.util.function.Function.identity(), (a, b) -> a));
    }

    private CardState toCardState(CardProgress p) {
        return new CardState(
                p.getEase(), p.getIntervalDays(), p.getReps(), p.getLapses(),
                p.getDueAt(), CardStateType.valueOf(p.getState()));
    }

    private Rating toSRsRating(ReviewRating r) {
        return Rating.valueOf(r.name());
    }

    private Card findCard(List<Card> cards, UUID cardId) {
        return cards.stream().filter(c -> c.getId().equals(cardId)).findFirst().orElse(null);
    }

    private ReviewQueueCard toQueueCard(Card card, CardProgress progress, Deck deck, String subjectName,
            CardState state) {
        Instant now = clock.instant();
        return new ReviewQueueCard(
                card.getId(), card.getDeckId(),
                deck.getTitle(), deck.getColor(), deck.getIcon(),
                subjectName,
                card.getType(), card.getFront(), card.getBack(),
                card.getExtra(), card.getTags(), card.getSourceTitle(), card.getSourceExcerpt(),
                state.state().name(), state.intervalDays(), state.reps(), state.lapses(),
                state.ease(), state.dueAt(), state.isDue(now),
                scheduler.previewLabels(state));
    }

    private Map<UUID, String> subjectNames(UUID userId, List<Deck> decks) {
        List<UUID> subjectIds = decks.stream()
                .map(Deck::getSubjectId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (subjectIds.isEmpty()) {
            return Map.of();
        }
        return subjectRepository.findAllByUserIdAndIdIn(userId, subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));
    }

    /**
     * Collects distinct subject IDs from the given decks.
     */
    private static List<UUID> subjectIds(UUID userId, Collection<Deck> decks) {
        return decks.stream()
                .map(Deck::getSubjectId)
                .filter(id -> id != null)
                .distinct()
                .toList();
    }

    private int countNew(List<Card> cards, Map<UUID, CardProgress> progress) {
        return (int) cards.stream()
                .filter(c -> progress.get(c.getId()) != null
                        && progress.get(c.getId()).getState().equals(CardProgress.STATE_NEW))
                .count();
    }

    private int countReview(List<Card> cards, Map<UUID, CardProgress> progress) {
        return cards.size() - countNew(cards, progress);
    }

    private int countNewInResult(List<ReviewQueueCard> result) {
        return (int) result.stream().filter(c -> c.state().equals("NEW")).count();
    }

    private int countReviewInResult(List<ReviewQueueCard> result) {
        return (int) result.stream().filter(c -> c.state().equals("REVIEW")).count();
    }

    private int countReviewsForCardToday(UUID userId, UUID cardId) {
        ZoneId zoneId = profileRepository.findById(userId)
                .map(p -> ZoneId.of(p.getTimezone()))
                .orElse(ZoneId.systemDefault());
        LocalDate today = clock.instant().atZone(zoneId).toLocalDate();
        return reviewLogRepository.findByCardIdAndUserIdOrderByReviewedAtDesc(cardId, userId).stream()
                .filter(log -> log.getReviewedAt() != null
                        && log.getReviewedAt().atZone(zoneId).toLocalDate().equals(today))
                .mapToInt(log -> 1)
                .sum();
    }
}
