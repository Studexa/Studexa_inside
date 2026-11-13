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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;
import com.studexa.deck.dto.CreateDeckRequest;
import com.studexa.deck.dto.DeckResponse;
import com.studexa.deck.dto.UpdateDeckRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/decks")
public class DeckController {

    private final DeckService deckService;

    public DeckController(DeckService deckService) {
        this.deckService = deckService;
    }

    @GetMapping
    public List<DeckResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return deckService.list(CurrentUser.id(jwt));
    }

    @GetMapping("/{id}")
    public DeckResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return deckService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DeckResponse create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateDeckRequest request) {
        return deckService.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public DeckResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody UpdateDeckRequest request) {
        return deckService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        deckService.delete(CurrentUser.id(jwt), id);
    }
}
