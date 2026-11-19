package com.studexa.ai;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.ai.dto.AiGenerateRequest;
import com.studexa.ai.dto.AiGenerateResponse;
import com.studexa.ai.dto.AiTask;
import com.studexa.ai.dto.AiTutorRequest;
import com.studexa.ai.dto.AiTutorResponse;
import com.studexa.common.CurrentUser;
import com.studexa.common.ApiException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private final AiService aiService;
    private final AiProperties properties;
    private final boolean betaEnabled;

    public AiController(AiService aiService, AiProperties properties,
            @Value("${app.beta.enabled:false}") boolean betaEnabled) {
        this.aiService = aiService;
        this.properties = properties;
        this.betaEnabled = betaEnabled;
    }

    @PostMapping("/generate")
    public AiGenerateResponse generate(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiGenerateRequest request) {
        requireOpenPrompts();
        return aiService.generate(CurrentUser.id(jwt), request);
    }

    @PostMapping("/tutor")
    public AiTutorResponse tutor(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AiTutorRequest request) {
        requireOpenPrompts();
        return aiService.tutor(CurrentUser.id(jwt), request);
    }

    @GetMapping("/source-limit")
    public SourceLimit sourceLimit() {
        requireOpenPrompts();
        return new SourceLimit(Math.min(20_000, properties.maxSourceChars()));
    }

    @GetMapping("/samples")
    public List<StudySample> samples() {
        return StudySample.CATALOG;
    }

    @PostMapping("/samples/generate")
    public AiGenerateResponse sampleGenerate(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SampleGenerateRequest request) {
        StudySample sample = StudySample.find(request.sampleId());
        requireBetaGemini();
        return aiService.generate(CurrentUser.id(jwt), new AiGenerateRequest(
                request.task(), null, sample.text(), sample.title(), request.count(), sample.language()));
    }

    @PostMapping("/samples/tutor")
    public AiTutorResponse sampleTutor(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SampleTutorRequest request) {
        StudySample sample = StudySample.find(request.sampleId());
        String question = sample.question(request.promptId());
        requireBetaGemini();
        return aiService.tutor(CurrentUser.id(jwt), new AiTutorRequest(question, sample.text(), sample.title()));
    }

    private void requireOpenPrompts() {
        if (betaEnabled) {
            throw ApiException.notFound("Choose a provided sample to study with Gemini.");
        }
    }

    private void requireBetaGemini() {
        if (betaEnabled && !properties.geminiConfigured()) {
            throw ApiException.serviceUnavailable("AI_NOT_CONFIGURED", "Gemini is temporarily unavailable.");
        }
    }

    public record SampleGenerateRequest(@NotBlank @Size(max = 64) String sampleId, @NotNull AiTask task,
            @NotNull @Min(1) @Max(5) Integer count) {}

    public record SampleTutorRequest(@NotBlank @Size(max = 64) String sampleId,
            @NotBlank @Size(max = 64) String promptId) {}

    public record SourceLimit(int maxSourceChars) {}
}
