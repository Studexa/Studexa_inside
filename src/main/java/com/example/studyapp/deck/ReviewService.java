package com.example.studyapp.deck;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.studyapp.common.ApiException;
import com.example.studyapp.deck.dto.ReviewAnswerRequest;
import com.example.studyapp.deck.dto.ReviewAnswerResponse;
import com.example.studyapp.deck.dto.ReviewQueueCard;
import com.example.studyapp.deck.dto.ReviewQueueResponse;
import com.example.studyapp.deck.dto.ReviewUndoResponse;
import com.example.studyapp.srs.CardState;
import com.example.studyapp.srs.CardStateType;
import com.example.studyapp.srs.QueueCard;
import com.example.studyapp.srs.Rating;
import com.example.studyapp.srs.Sm2Scheduler;
import com.example.studyapp.srs.StudyQueueBuilder;
import com.example.studyapp.subject.Subject;
import com.example.studyapp.subject.SubjectRepository;

@Service
public class ReviewService {

    private final DeckService deckService;
    private final CardRepository cardRepository;
    private final CardProgressRepository progressRepository;
    private final ReviewLogRepository reviewLogRepository;
    private final SubjectRepository subjectRepository;
    private final Sm2Scheduler scheduler;
    private final StudyQueueBuilder queueBuilder;

    public ReviewService(DeckService deckService, CardRepository cardRepository,
            CardProgressRepository progressRepository, ReviewLogRepository reviewLogRepository,
            SubjectRepository subjectRepository) {
        this.deckService = deckService;
        this.cardRepository = cardRepository;
        this.progressRepository = progressRepository;
        this.reviewLogRepository = reviewLogRepository;
        this.subjectRepository = subjectRepository;
        this.scheduler = new Sm2Scheduler();
        this.queueBuilder = new StudyQueueBuilder();
    }

    @Transactional(readOnly = true)
    public ReviewQueueResponse getQueue(UUID userId, UUID deckId) {
        if (deckId != null) {
            deckService.requireOwned(userId, deckId);
        }

        Instant now = Instant.now();

        List<Card> cards;
        if (deckId != null) {
            cards = cardRepository.findAllByDeckIdAndUserIdOrderByCreatedAtAsc(deckId, userId);
        } else {
            cards = cardRepository.findTop80ByUserIdOrderByCreatedAtDesc(userId);
        }

        if (cards.isEmpty()) {
            return new ReviewQueueResponse(List.of(), 0, 0, now);
        }

        Map<UUID, CardProgress> progress = progressByCard(userId, cards);

        // Load distinct decks for title/color/icon/subject info
        List<UUID> deckIds = cards.stream().map(Card::getDeckId).distinct().toList();
        Map<UUID, Deck> decks = deckIds.stream()
                .map(id -> deckService.requireOwned(userId, id))
                .collect(Collectors.toMap(Deck::getId, java.util.function.Function.identity()));

        List<QueueCard> queueCards = new ArrayList<>();
        for (Card card : cards) {
            CardProgress p = progress.get(card.getId());
            CardState state = toCardState(p);
            queueCards.add(new QueueCard(
                    card.getId(), state, card.isSuspended(), card.isStarred()));
        }

        Map<UUID, String> subjectNames = subjectNames(userId, new ArrayList<>(decks.values()));

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
                .orElseGet(() -> CardProgress.newFor(cardId, userId));

        CardState current = toCardState(progress);
        Rating rating = toSRsRating(request.rating());
        Instant now = Instant.now();

        CardState next = scheduler.schedule(current, rating, now);
        Map<Rating, String> labels = scheduler.previewLabels(next);

        ReviewLog log = ReviewLog.create(
                UUID.randomUUID(), clientReviewId, cardId, userId,
                request.rating().name(), now, request.msTaken(), 0,
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
                0, false, 1, null);
    }

    @Transactional
    public ReviewUndoResponse undo(UUID userId, UUID cardId) {
        Card card = requireOwned(userId, cardId);

        List<ReviewLog> logs = reviewLogRepository.findByCardIdAndUserIdOrderByReviewedAtDesc(cardId, userId);
        if (logs.isEmpty()) {
            throw ApiException.notFound("No review to undo for this card.");
        }

        ReviewLog last = logs.get(0);

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

        CardState state = toCardState(progress);
        Map<Rating, String> labels = scheduler.previewLabels(state);

        return new ReviewUndoResponse(
                card.getId(), card.getDeckId(), card.getType(),
                card.getFront(), card.getBack(), card.getExtra(), card.getTags(),
                state.state().name(), state.intervalDays(), state.reps(), state.lapses(),
                state.ease(), state.dueAt(), labels, Instant.now());
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
        Instant now = Instant.now();
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
}
