package com.studexa.ai;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.studexa.ai.dto.AiGenerateRequest;
import com.studexa.ai.dto.AiGenerateResponse;
import com.studexa.ai.dto.AiTask;
import com.studexa.ai.dto.AiTutorRequest;
import com.studexa.ai.dto.AiTutorResponse;
import com.studexa.common.CurrentUser;
import com.studexa.common.ApiException;
import com.studexa.notes.NoteFileExtractor;
import com.studexa.slides.SlideDraft;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/v1/ai")
public class AiController {
    private static final long MAX_SLIDE_FILE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_SLIDE_PROMPT = 1000;

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
        return new SourceLimit(maxSourceChars());
    }

    @PostMapping(value = "/slides/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<NoteFileExtractor.ExtractedText> extractSlideText(
            @RequestPart(value = "file", required = false) MultipartFile file) {
        requireOpenPrompts();
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Choose a file that is not empty.");
        }
        if (file.getSize() > MAX_SLIDE_FILE_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "Files must be 5 MB or smaller.");
        }
        NoteFileExtractor.ExtractedText extracted;
        try {
            String name = file.getOriginalFilename();
            extracted = NoteFileExtractor.extract(name == null ? "" : name, file.getBytes());
        } catch (IOException ex) {
            throw ApiException.badRequest("INVALID_FILE", "The file upload could not be read.");
        }
        if (extracted.text().isBlank()) {
            throw ApiException.badRequest("NO_READABLE_TEXT", "This file contains no readable text.");
        }
        String text = extracted.text().replace("\r\n", "\n").replace('\r', '\n');
        int end = Math.min(maxSourceChars(), text.length());
        if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
        var result = new NoteFileExtractor.ExtractedText(text.substring(0, end).stripTrailing(),
                extracted.truncated() || end < text.length());
        if (result.text().isBlank()) {
            throw ApiException.badRequest("NO_READABLE_TEXT", "This file contains no readable text within the source limit.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }

    @PostMapping("/slides/generate")
    public ResponseEntity<SlideDraft> generateSlides(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SlideGenerateRequest request) {
        requireOpenPrompts();
        boolean source = request.mode() == SlideMode.SOURCE;
        if (request.text().length() > (source ? maxSourceChars() : MAX_SLIDE_PROMPT)) {
            throw ApiException.badRequest("SLIDE_INPUT_TOO_LONG", "The slide source is too long.");
        }
        if (!"en".equals(request.language()) && !"km".equals(request.language())) {
            throw ApiException.badRequest("INVALID_LANGUAGE", "Choose English or Khmer.");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(aiService.generateSlides(CurrentUser.id(jwt), request.text(), request.language(), source));
    }

    @PostMapping("/samples/slides")
    public ResponseEntity<SlideDraft> generateSampleSlides(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SampleSlidesRequest request) {
        StudySample sample = StudySample.find(request.sampleId());
        requireBetaGemini();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(aiService.generateSlides(CurrentUser.id(jwt), sample.text(), sample.language(), true));
    }

    private int maxSourceChars() {
        return Math.min(20_000, properties.maxSourceChars());
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
        List<String> previousQuestions = request.previousPromptIds() == null ? List.of()
                : request.previousPromptIds().stream().map(sample::question).toList();
        requireBetaGemini();
        return aiService.tutor(CurrentUser.id(jwt), new AiTutorRequest(question, sample.text(), sample.title()),
                previousQuestions);
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
            @NotBlank @Size(max = 64) String promptId,
            @Size(max = 3) List<@NotBlank @Size(max = 64) String> previousPromptIds) {}

    public record SourceLimit(int maxSourceChars) {}

    public enum SlideMode { PROMPT, SOURCE }

    public record SlideGenerateRequest(@NotNull SlideMode mode, @NotBlank @Size(max = 20_000) String text,
            @NotBlank @Size(max = 2) String language) {}

    public record SampleSlidesRequest(@NotBlank @Size(max = 64) String sampleId) {}
}
