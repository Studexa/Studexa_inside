package com.studexa.deck;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;
import com.studexa.deck.dto.ReviewAnswerRequest;
import com.studexa.deck.dto.ReviewAnswerResponse;
import com.studexa.deck.dto.ReviewQueueResponse;
import com.studexa.deck.dto.ReviewUndoResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/review")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/queue")
    public ReviewQueueResponse queue(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID deckId) {
        return reviewService.getQueue(CurrentUser.id(jwt), deckId);
    }

    @PostMapping("/answer")
    @ResponseStatus(HttpStatus.OK)
    public ReviewAnswerResponse answer(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ReviewAnswerRequest request) {
        return reviewService.answer(CurrentUser.id(jwt), request.cardId(), request);
    }

    @PostMapping("/undo")
    @ResponseStatus(HttpStatus.OK)
    public ReviewUndoResponse undo(@AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID cardId) {
        return reviewService.undo(CurrentUser.id(jwt), cardId);
    }
}
