package com.studexa.deck;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.text.Normalizer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.deck.dto.BulkCreateCardsRequest;
import com.studexa.deck.dto.CardResponse;
import com.studexa.deck.dto.CreateCardRequest;
import com.studexa.deck.dto.DueCardResponse;
import com.studexa.deck.dto.UpdateCardRequest;
import com.studexa.subject.Subject;
import com.studexa.subject.SubjectRepository;

@Service
public class CardService {

    private final DeckService deckService;
    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final CardProgressRepository progressRepository;
    private final SubjectRepository subjectRepository;
    private final Clock clock;

    public CardService(DeckService deckService, DeckRepository deckRepository,
            CardRepository cardRepository, CardProgressRepository progressRepository,
            SubjectRepository subjectRepository, Clock clock) {
        this.deckService = deckService;
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.progressRepository = progressRepository;
        this.subjectRepository = subjectRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DueCardResponse> due(UUID userId, int limit) {
        int capped = Math.min(100, Math.max(1, limit));
        List<UUID> ids = progressRepository.findDueCardIds(userId, clock.instant(), capped);
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Card> cards = cardRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Card::getId, Function.identity()));
        Map<UUID, Deck> decks = deckRepository.findTop100ByUserIdOrderByUpdatedAtDesc(userId).stream()
                .collect(Collectors.toMap(Deck::getId, Function.identity()));
        Map<UUID, String> subjects = new java.util.HashMap<>();
        for (Deck deck : decks.values()) {
            if (deck.getSubjectId() != null && !subjects.containsKey(deck.getSubjectId())) {
                String name = subjectRepository.findByIdAndUserId(deck.getSubjectId(), userId)
                        .map(Subject::getName).orElse(null);
                if (name != null) {
                    subjects.put(deck.getSubjectId(), name);
                }
            }
        }

        List<DueCardResponse> result = new ArrayList<>();
        for (UUID id : ids) {
            Card card = cards.get(id);
            Deck deck = card == null ? null : decks.get(card.getDeckId());
            if (card != null && deck != null) {
                result.add(DueCardResponse.from(card, deck,
                        deck.getSubjectId() == null ? null : subjects.get(deck.getSubjectId())));
            }
        }
        return result;
    }

    @Transactional
    public CardResponse review(UUID userId, UUID cardId, ReviewRating rating) {
        Card card = requireOwned(userId, cardId);
        CardProgress progress = progressRepository.findByCardIdAndUserId(cardId, userId)
                .orElseGet(() -> CardProgress.newFor(cardId, userId, clock.instant()));

        Instant now = clock.instant();
        int interval = progress.getIntervalDays();
        int reps = progress.getReps() + 1;
        double ease = progress.getEase();
        Instant dueAt;

        switch (rating) {
            case AGAIN -> {
                ease = Math.max(1.3, ease - 0.2);
                interval = 0;
                if (progress.getReps() > 0) {
                    progress.setLapses(progress.getLapses() + 1);
                }
                dueAt = now.plus(10, ChronoUnit.MINUTES);
            }
            case HARD -> {
                ease = Math.max(1.3, ease - 0.15);
                interval = interval == 0 ? 1 : Math.max(1, (int) Math.ceil(interval * 1.2));
                dueAt = now.plus(interval, ChronoUnit.DAYS);
            }
            case GOOD -> {
                interval = interval == 0 ? 1 : Math.max(1, (int) Math.round(interval * ease));
                dueAt = now.plus(interval, ChronoUnit.DAYS);
            }
            case EASY -> {
                ease = Math.min(3.2, ease + 0.15);
                interval = interval == 0 ? 4 : Math.max(2, (int) Math.round(interval * ease * 1.3));
                dueAt = now.plus(interval, ChronoUnit.DAYS);
            }
            default -> throw ApiException.badRequest("INVALID_RATING", "Choose a valid review rating.");
        }

        progress.setEase(ease);
        progress.setIntervalDays(interval);
        progress.setReps(reps);
        progress.setDueAt(dueAt);
        progress.setState(interval == 0 ? "LEARNING" : "REVIEW");
        progress.setLastReviewedAt(now);
        progressRepository.save(progress);

        return CardResponse.from(card, false);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> list(UUID userId, UUID deckId) {
        deckService.requireOwned(userId, deckId);
        List<Card> cards = cardRepository.findTop100ByDeckIdAndUserIdOrderByCreatedAtAsc(deckId, userId);
        Map<UUID, CardProgress> progress = progressByCard(userId, cards);
        Instant now = clock.instant();
        return cards.stream()
                .map(card -> CardResponse.from(card, isDue(card, progress.get(card.getId()), now)))
                .toList();
    }

    @Transactional
    public CardResponse create(UUID userId, UUID deckId, CreateCardRequest request) {
        Deck deck = deckService.requireOwned(userId, deckId);
        Card card = buildCard(userId, deckId, request);
        Card saved = cardRepository.save(card);
        progressRepository.save(CardProgress.newFor(saved.getId(), userId, clock.instant()));
        touchDeck(deck);
        return CardResponse.from(saved, true);
    }

    @Transactional
    public List<CardResponse> bulkCreate(UUID userId, UUID deckId, BulkCreateCardsRequest request) {
        Deck deck = deckService.requireOwned(userId, deckId);
        List<Card> cards = request.cards().stream()
                .map(item -> buildCard(userId, deckId, item))
                .toList();
        List<Card> saved = cardRepository.saveAll(cards);
        List<CardProgress> progress = new ArrayList<>(saved.size());
        Instant now = clock.instant();
        for (Card card : saved) {
            progress.add(CardProgress.newFor(card.getId(), userId, now));
        }
        progressRepository.saveAll(progress);
        touchDeck(deck);
        return saved.stream().map(card -> CardResponse.from(card, true)).toList();
    }

    @Transactional
    public CardResponse update(UUID userId, UUID cardId, UpdateCardRequest request) {
        Card card = requireOwned(userId, cardId);

        if (request.type() != null) {
            card.setType(request.type());
        }
        if (request.front() != null) {
            card.setFront(normalizeText(requiredText(request.front(), "Card front")));
        }
        if (request.back() != null) {
            card.setBack(normalizeText(requiredText(request.back(), "Card back")));
        }
        if (request.extra() != null) {
            card.setExtra(sanitizeExtra(request.extra()));
        }
        if (request.tags() != null) {
            card.setTags(sanitizeTags(request.tags()));
        }
        if (request.starred() != null) {
            card.setStarred(request.starred());
        }
        if (request.suspended() != null) {
            card.setSuspended(request.suspended());
        }
        if (request.sourceTitle() != null) {
            card.setSourceTitle(blankToNull(request.sourceTitle()));
        }
        if (request.sourceExcerpt() != null) {
            card.setSourceExcerpt(blankToNull(request.sourceExcerpt()));
        }

        validateChoices(card.getType(), card.getBack(), card.getExtra());
        Card saved = cardRepository.save(card);
        deckRepository.findById(saved.getDeckId()).ifPresent(this::touchDeck);

        boolean due = progressRepository.findByCardIdAndUserId(cardId, userId)
                .map(progress -> !saved.isSuspended() && progress.isDue(clock.instant()))
                .orElse(!saved.isSuspended());
        return CardResponse.from(saved, due);
    }

    @Transactional
    public void delete(UUID userId, UUID cardId) {
        Card card = requireOwned(userId, cardId);
        cardRepository.delete(card);
        deckRepository.findById(card.getDeckId()).ifPresent(this::touchDeck);
    }

    private Card requireOwned(UUID userId, UUID cardId) {
        return cardRepository.findByIdAndUserId(cardId, userId)
                .orElseThrow(() -> ApiException.notFound("Card not found."));
    }

    private Card buildCard(UUID userId, UUID deckId, CreateCardRequest request) {
        if (request.type() == null) {
            throw ApiException.badRequest("INVALID_TYPE", "Card type is required.");
        }
        String front = normalizeText(requiredText(request.front(), "Card front"));
        String back = normalizeText(requiredText(request.back(), "Card back"));
        Map<String, Object> extra = sanitizeExtra(request.extra());
        validateChoices(request.type(), back, extra);
        return Card.create(
                deckId,
                userId,
                request.type(),
                front,
                back,
                extra,
                sanitizeTags(request.tags()),
                blankToNull(request.sourceTitle()),
                limit(blankToNull(request.sourceExcerpt()), 1000));
    }

    private Map<UUID, CardProgress> progressByCard(UUID userId, List<Card> cards) {
        if (cards.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = cards.stream().map(Card::getId).toList();
        return progressRepository.findAllByUserIdAndCardIdIn(userId, ids).stream()
                .collect(Collectors.toMap(CardProgress::getCardId, Function.identity(), (a, b) -> a));
    }

    private static boolean isDue(Card card, CardProgress progress, Instant now) {
        if (card.isSuspended()) {
            return false;
        }
        return progress == null || progress.isDue(now);
    }

    private void touchDeck(Deck deck) {
        deck.touch();
        deckRepository.save(deck);
    }

    private static void validateChoices(CardType type, String back, Map<String, Object> extra) {
        if (type != CardType.MULTIPLE_CHOICE) {
            return;
        }
        Object raw = extra.get("choices");
        if (!(raw instanceof List<?> choices) || choices.size() < 2) {
            throw ApiException.badRequest(
                    "INVALID_CHOICES", "Multiple-choice cards need at least two choices.");
        }
        boolean containsAnswer = choices.stream()
                .map(String::valueOf)
                .anyMatch(choice -> choice.equals(back));
        if (!containsAnswer) {
            throw ApiException.badRequest(
                    "INVALID_CHOICES", "The correct answer must be one of the choices.");
        }
    }

    private static Map<String, Object> sanitizeExtra(Map<String, Object> extra) {
        return extra == null ? Map.of() : extra;
    }

    private static List<String> sanitizeTags(List<String> tags) {
        if (tags == null) {
            return List.of();
        }
        return tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(String::trim)
                .map(tag -> limit(tag, 40))
                .distinct()
                .limit(20)
                .toList();
    }

    private static String requiredText(String value, String label) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("INVALID_CARD", label + " cannot be empty.");
        }
        if (trimmed.length() > 5000) {
            throw ApiException.badRequest("INVALID_CARD", label + " must be 5000 characters or fewer.");
        }
        return trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Normalizes text to Unicode NFC so visually identical Khmer strings
     * compare equal (BUG-G03).
     */
    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        return Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
