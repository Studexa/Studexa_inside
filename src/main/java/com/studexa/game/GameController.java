package com.studexa.game;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.common.CurrentUser;
import com.studexa.game.dto.BadgeDto;
import com.studexa.game.dto.GameStateDto;
import com.studexa.game.dto.XpEventDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/v1/game")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    @GetMapping("/state")
    public GameStateDto state(@AuthenticationPrincipal Jwt jwt) {
        return gameService.getState(CurrentUser.id(jwt));
    }

    @GetMapping("/badges")
    public List<BadgeDto> badges(@AuthenticationPrincipal Jwt jwt) {
        return gameService.getBadges(CurrentUser.id(jwt));
    }

    @GetMapping("/xp-history")
    public List<XpEventDto> xpHistory(@AuthenticationPrincipal Jwt jwt,
            @Positive @org.springframework.web.bind.annotation.RequestParam(defaultValue = "20") int limit) {
        return gameService.getXpHistory(CurrentUser.id(jwt), limit);
    }

    /**
     * Consumes one streak freeze already purchased from the shop.
     */
    @PostMapping("/streak-freeze/consume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void consumeStreakFreeze(@AuthenticationPrincipal Jwt jwt) {
        gameService.consumeStreakFreeze(CurrentUser.id(jwt));
    }

    public record ConsumeFreezeRequest(@NotNull String dummy) {
    }
}
