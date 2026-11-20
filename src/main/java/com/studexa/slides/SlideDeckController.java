package com.studexa.slides;

import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;

@RestController
@RequestMapping("/api/v1/slide-decks")
public class SlideDeckController {
    private final SlideDeckService decks;

    public SlideDeckController(SlideDeckService decks) {
        this.decks = decks;
    }

    @GetMapping
    public ResponseEntity<List<SlideDeckService.DeckSummary>> listSlideDecks(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.list(CurrentUser.id(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SlideDeckService.Deck> getSlideDeck(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.get(CurrentUser.id(jwt), id));
    }

    @PostMapping
    public ResponseEntity<SlideDeckService.Deck> saveSlideDeck(@AuthenticationPrincipal Jwt jwt, @RequestBody SlideDraft request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(decks.save(CurrentUser.id(jwt), request));
    }
}
