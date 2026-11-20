package com.studexa.slides;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record SlideDraft(UUID id, Content content) {

    public static final int SLIDE_COUNT = 5;
    public static final int QUESTION_COUNT = 4;

    public record Content(String title, List<Slide> slides, List<QuizQuestion> questions) {
        public boolean valid() {
            return text(title, 120)
                    && slides != null && slides.size() == SLIDE_COUNT
                    && slides.stream().allMatch(slide -> slide != null && slide.valid())
                    && questions != null && questions.size() == QUESTION_COUNT
                    && questions.stream().allMatch(question -> question != null && question.valid());
        }
    }

    public record Slide(String title, List<String> bullets) {
        private boolean valid() {
            return text(title, 100) && bullets != null && bullets.size() >= 2 && bullets.size() <= 3
                    && bullets.stream().allMatch(bullet -> text(bullet, 180));
        }
    }

    public record QuizQuestion(String question, List<String> choices, Integer correctIndex, String explanation) {
        private boolean valid() {
            return text(question, 240) && text(explanation, 280)
                    && correctIndex != null && correctIndex >= 0 && correctIndex < 4
                    && choices != null && choices.size() == 4 && choices.stream().allMatch(choice -> text(choice, 120))
                    && choices.stream().map(choice -> choice.strip().toLowerCase(Locale.ROOT)).distinct().count() == 4;
        }
    }

    private static boolean text(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit
                && value.codePoints().noneMatch(code -> Character.isISOControl(code) && !Character.isWhitespace(code));
    }
}
