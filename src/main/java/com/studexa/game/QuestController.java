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
import com.studexa.game.dto.QuestDto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/v1/quests")
public class QuestController {

    private final QuestService questService;

    public QuestController(QuestService questService) {
        this.questService = questService;
    }

    /**
     * Returns today's quests for the logged-in student, creating them lazily
     * if they don't exist yet (Section 7.7).
     */
    @GetMapping("/today")
    public List<QuestDto> today(@AuthenticationPrincipal Jwt jwt) {
        return questService.getToday(CurrentUser.id(jwt));
    }

    /**
     * Report progress on a quest. Called by the frontend after study actions
     * (reviews, focus sessions, notes, mini-games). The amount is derived
     * server-side from the source; the client must not send it.
     */
    @PostMapping("/progress")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void addProgress(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody QuestProgressRequest request) {
        questService.addProgress(
                CurrentUser.id(jwt), request.code(), request.source(),
                request.referenceId() != null
                        ? UUID.fromString(request.referenceId())
                        : null);
    }

    public record QuestProgressRequest(
            @NotBlank String code,
            @NotBlank String source,
            String referenceId) {
    }
}
