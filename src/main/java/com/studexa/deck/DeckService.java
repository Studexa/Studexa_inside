package com.studexa.deck;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.deck.dto.CreateDeckRequest;
import com.studexa.deck.dto.DeckResponse;
import com.studexa.deck.dto.UpdateDeckRequest;
import com.studexa.subject.Subject;
import com.studexa.subject.SubjectRepository;

@Service
public class DeckService {

    private final DeckRepository deckRepository;
    private final CardRepository cardRepository;
    private final CardProgressRepository progressRepository;
    private final SubjectRepository subjectRepository;
    private final Clock clock;

    public DeckService(DeckRepository deckRepository, CardRepository cardRepository,
            CardProgressRepository progressRepository, SubjectRepository subjectRepository,
            Clock clock) {
        this.deckRepository = deckRepository;
        this.cardRepository = cardRepository;
        this.progressRepository = progressRepository;
        this.subjectRepository = subjectRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DeckResponse> list(UUID userId) {
        List<Deck> decks = deckRepository.findTop100ByUserIdOrderByUpdatedAtDesc(userId);
        Map<UUID, String> subjects = subjectNames(userId, decks);
        Instant now = clock.instant();
        return decks.stream()
                .map(deck -> DeckResponse.from(
                        deck,
                        deck.getSubjectId() == null ? null : subjects.get(deck.getSubjectId()),
                        cardRepository.countByDeckIdAndUserId(deck.getId(), userId),
                        progressRepository.countDueInDeck(userId, deck.getId(), now)))
                .toList();
    }

    @Transactional(readOnly = true)
    public DeckResponse get(UUID userId, UUID deckId) {
        Deck deck = requireOwned(userId, deckId);
        String subjectName = deck.getSubjectId() == null ? null
                : subjectRepository.findByIdAndUserId(deck.getSubjectId(), userId)
                        .map(Subject::getName).orElse(null);
        return DeckResponse.from(
                deck,
                subjectName,
                cardRepository.countByDeckIdAndUserId(deckId, userId),
                progressRepository.countDueInDeck(userId, deckId, clock.instant()));
    }

    /**
     * Batch-load decks owned by the user, keyed by id. Used by the review queue
     * to avoid N+1 queries (BUG-H02).
     */
    @Transactional(readOnly = true)
    public Map<UUID, Deck> findByIds(UUID userId, List<UUID> deckIds) {
        if (deckIds.isEmpty()) {
            return Map.of();
        }
        return deckRepository.findAllByIdInAndUserId(deckIds, userId).stream()
                .collect(Collectors.toMap(Deck::getId, d -> d, (a, b) -> a));
    }

    @Transactional
    public DeckResponse create(UUID userId, CreateDeckRequest request) {
        UUID subjectId = validateSubject(userId, request.subjectId());
        Deck deck = Deck.create(
                userId,
                subjectId,
                requiredTitle(request.title()),
                blankToNull(request.description()),
                colorOrDefault(request.color()),
                iconOrDefault(request.icon()));
        Deck saved = deckRepository.save(deck);
        return DeckResponse.from(saved, subjectName(userId, subjectId), 0, 0);
    }

    @Transactional
    public DeckResponse update(UUID userId, UUID deckId, UpdateDeckRequest request) {
        Deck deck = requireOwned(userId, deckId);

        if (request.title() != null) {
            deck.setTitle(requiredTitle(request.title()));
        }
        if (request.description() != null) {
            deck.setDescription(blankToNull(request.description()));
        }
        if (request.subjectId() != null) {
            deck.setSubjectId(validateSubject(userId, request.subjectId()));
        }
        if (request.color() != null) {
            deck.setColor(colorOrDefault(request.color()));
        }
        if (request.icon() != null) {
            deck.setIcon(iconOrDefault(request.icon()));
        }

        Deck saved = deckRepository.save(deck);
        return get(userId, saved.getId());
    }

    @Transactional
    public void delete(UUID userId, UUID deckId) {
        deckRepository.delete(requireOwned(userId, deckId));
    }

    Deck requireOwned(UUID userId, UUID deckId) {
        return deckRepository.findByIdAndUserId(deckId, userId)
                .orElseThrow(() -> ApiException.notFound("Deck not found."));
    }

    private UUID validateSubject(UUID userId, UUID subjectId) {
        if (subjectId == null) {
            return null;
        }
        subjectRepository.findByIdAndUserId(subjectId, userId)
                .orElseThrow(() -> ApiException.badRequest(
                        "INVALID_SUBJECT", "Choose one of your own subjects."));
        return subjectId;
    }

    private String subjectName(UUID userId, UUID subjectId) {
        if (subjectId == null) {
            return null;
        }
        return subjectRepository.findByIdAndUserId(subjectId, userId)
                .map(Subject::getName)
                .orElse(null);
    }

    private Map<UUID, String> subjectNames(UUID userId, List<Deck> decks) {
        List<UUID> ids = decks.stream()
                .map(Deck::getSubjectId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return subjectRepository.findAllByUserIdAndIdIn(userId, ids).stream()
                .collect(Collectors.toMap(Subject::getId, Subject::getName, (a, b) -> a));
    }

    private static String requiredTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.badRequest("INVALID_TITLE", "Deck title cannot be empty.");
        }
        if (trimmed.length() > 160) {
            throw ApiException.badRequest("INVALID_TITLE", "Deck title must be 160 characters or fewer.");
        }
        return trimmed;
    }

    private static String colorOrDefault(String color) {
        String trimmed = color == null ? "" : color.trim();
        return trimmed.isEmpty() ? Deck.DEFAULT_COLOR : trimmed;
    }

    private static String iconOrDefault(String icon) {
        String trimmed = icon == null ? "" : icon.trim();
        return trimmed.isEmpty() ? Deck.DEFAULT_ICON : trimmed;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
