package com.studexa.deck;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;
import com.studexa.deck.dto.BulkCreateCardsRequest;
import com.studexa.deck.dto.CardResponse;
import com.studexa.deck.dto.CreateCardRequest;
import com.studexa.deck.dto.DueCardResponse;
import com.studexa.deck.dto.UpdateCardRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/decks/{deckId}/cards")
    public List<CardResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID deckId) {
        return cardService.list(CurrentUser.id(jwt), deckId);
    }

    @GetMapping("/cards/due")
    public List<DueCardResponse> due(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "50") int limit) {
        return cardService.due(CurrentUser.id(jwt), limit);
    }

    @PostMapping("/decks/{deckId}/cards")
    @ResponseStatus(HttpStatus.CREATED)
    public CardResponse create(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID deckId,
            @Valid @RequestBody CreateCardRequest request) {
        return cardService.create(CurrentUser.id(jwt), deckId, request);
    }

    @PostMapping("/decks/{deckId}/cards/bulk")
    @ResponseStatus(HttpStatus.CREATED)
    public List<CardResponse> bulkCreate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID deckId,
            @Valid @RequestBody BulkCreateCardsRequest request) {
        return cardService.bulkCreate(CurrentUser.id(jwt), deckId, request);
    }

    @PutMapping("/cards/{id}")
    public CardResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UpdateCardRequest request) {
        return cardService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/cards/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        cardService.delete(CurrentUser.id(jwt), id);
    }
}
