package com.studexa.library;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.studexa.ai.AiProperties;
import com.studexa.ai.AiService;
import com.studexa.ai.dto.AiTutorRequest;
import com.studexa.ai.dto.AiTutorResponse;
import com.studexa.common.ApiException;
import com.studexa.common.CurrentUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@RestController
@RequestMapping("/api/v1/library/readings")
public class ReadingController {
    private final WikisourceReadings readings;
    private final AiService ai;
    private final AiProperties properties;
    private final boolean betaEnabled;

    public ReadingController(WikisourceReadings readings, AiService ai, AiProperties properties,
            @Value("${app.beta.enabled:false}") boolean betaEnabled) {
        this.readings = readings;
        this.ai = ai;
        this.properties = properties;
        this.betaEnabled = betaEnabled;
    }

    @GetMapping
    public List<WikisourceReadings.Edition> listReadings() { return readings.list(); }

    @GetMapping("/{id}")
    public ReadingResponse getReading(@PathVariable String id) {
        var reading = readings.get(id);
        return new ReadingResponse(reading.edition(), reading.blocks(), limit());
    }

    @PostMapping("/{id}/ask")
    public AiTutorResponse askReading(@PathVariable String id, @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AskRequest request) {
        var reading = readings.get(id);
        String text = passage(reading, request, limit());
        if (betaEnabled && !properties.geminiConfigured()) {
            throw ApiException.serviceUnavailable("AI_NOT_CONFIGURED", "Gemini is temporarily unavailable.");
        }
        return ai.tutor(CurrentUser.id(jwt), new AiTutorRequest(prompt(request.action(), request.language()), text,
                reading.edition().title()));
    }

    private int limit() { return Math.min(20_000, properties.maxSourceChars()); }

    static String passage(WikisourceReadings.Reading reading, AskRequest range, int maximum) {
        if (range.revision() != reading.edition().revision()) {
            throw ApiException.conflict("READING_CHANGED", "Reload this reading before choosing a passage.");
        }
        var blocks = reading.blocks();
        int start = range.startBlock(), end = range.endBlock();
        int from = range.startOffset(), to = range.endOffset();
        if (start < 0 || end < start || end >= blocks.size() || from < 0 || to < 0
                || !boundary(blocks.get(start).text(), from) || !boundary(blocks.get(end).text(), to)
                || (start == end && to <= from)) throw invalidSelection();

        var selected = new StringBuilder();
        for (int index = start; index <= end; index++) {
            String block = blocks.get(index).text();
            if (index > start) selected.append("\n\n");
            selected.append(block, index == start ? from : 0, index == end ? to : block.length());
            if (selected.length() > maximum) {
                throw ApiException.badRequest("READING_SELECTION_TOO_LONG", "Select a shorter passage to study.");
            }
        }
        if (selected.toString().isBlank()) throw invalidSelection();
        return selected.toString().strip();
    }

    private static boolean boundary(String text, int index) {
        return index <= text.length() && (index == text.length() || !Character.isLowSurrogate(text.charAt(index)));
    }

    private static ApiException invalidSelection() {
        return ApiException.badRequest("INVALID_READING_SELECTION", "Select text within the reading and try again.");
    }

    private static String prompt(Action action, String language) {
        if ("km".equals(language)) return switch (action) {
            case EXPLAIN -> "សូមពន្យល់អត្ថបទដែលបានជ្រើសជាភាសាខ្មែរ ដោយប្រើពាក្យសាមញ្ញ។";
            case SUMMARIZE -> "សូមសង្ខេបគំនិតសំខាន់ៗពីអត្ថបទដែលបានជ្រើសជាភាសាខ្មែរ។";
            case PRACTICE -> "សូមសួរសំណួរអនុវត្តមួយជាភាសាខ្មែរ ដែលអាចឆ្លើយបានពីអត្ថបទនេះ។ កុំបង្ហាញចម្លើយ។";
        };
        return switch (action) {
            case EXPLAIN -> "Explain the selected passage in plain English.";
            case SUMMARIZE -> "Summarize the main ideas in the selected passage in English.";
            case PRACTICE -> "Ask one practice question in English that can be answered from the passage. Keep the answer hidden.";
        };
    }

    public enum Action { EXPLAIN, SUMMARIZE, PRACTICE }

    public record AskRequest(@NotNull @Min(1) Long revision, @NotNull @Min(0) Integer startBlock,
            @NotNull @Min(0) Integer startOffset, @NotNull @Min(0) Integer endBlock,
            @NotNull @Min(0) Integer endOffset, @NotNull @Pattern(regexp = "en|km") String language,
            @NotNull Action action) {}

    public record ReadingResponse(WikisourceReadings.Edition edition, List<WikisourceReadings.Block> blocks,
                                  int maxSourceChars) {}
}
