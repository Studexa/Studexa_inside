package com.studexa.slides;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.studexa.common.ApiException;
import com.studexa.quiz.Question;
import com.studexa.quiz.QuestionRepository;
import com.studexa.quiz.Quiz;
import com.studexa.quiz.QuizRepository;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class SlideDeckService {
    private static final int QUESTION_SECONDS = 30;
    private static final TypeReference<List<SlideDraft.Slide>> SLIDES = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final QuizRepository quizzes;
    private final QuestionRepository questions;
    private final ObjectMapper json;

    public SlideDeckService(JdbcClient jdbc, QuizRepository quizzes, QuestionRepository questions, ObjectMapper json) {
        this.jdbc = jdbc;
        this.quizzes = quizzes;
        this.questions = questions;
        this.json = json;
    }

    public record DeckSummary(UUID id, String title, Instant createdAt) {}
    public record Deck(UUID id, String title, UUID quizId, List<SlideDraft.Slide> slides, Instant createdAt) {}

    public List<DeckSummary> list(UUID owner) {
        return jdbc.sql("select id, title, created_at from slide_decks where owner_id = :owner order by created_at desc, id")
                .param("owner", owner).query((rs, row) -> new DeckSummary(rs.getObject("id", UUID.class),
                        rs.getString("title"), rs.getObject("created_at", OffsetDateTime.class).toInstant())).list();
    }

    public Deck get(UUID owner, UUID id) {
        return find(owner, id).orElseThrow(() -> ApiException.notFound("Slide deck not found."));
    }

    @Transactional
    public Deck save(UUID owner, SlideDraft request) {
        if (request.id() == null || request.content() == null || !request.content().valid()) {
            throw ApiException.badRequest("INVALID_SLIDE_DRAFT", "The slide and question draft is incomplete.");
        }

        // Match the existing account lock convention so concurrent saves cannot create duplicate quizzes.
        jdbc.sql("select id from users where id = :owner for no key update").param("owner", owner)
                .query(UUID.class).optional().orElseThrow(() -> ApiException.notFound("Account not found."));
        Optional<Deck> existing = find(owner, request.id());
        if (existing.isPresent()) return existing.get();

        var content = request.content();
        String title = content.title().strip();
        Quiz quiz = quizzes.saveAndFlush(new Quiz(owner, title, null, null, Quiz.Visibility.PRIVATE));
        var quizQuestions = new ArrayList<Question>();
        for (var item : content.questions()) {
            List<String> choices = item.choices().stream().map(String::strip).toList();
            var question = new Question(quiz.getId(), item.question().strip(), Question.Type.MULTIPLE_CHOICE,
                    json.writeValueAsString(choices), choices.get(item.correctIndex()), item.explanation().strip(),
                    null, null, null, Question.Difficulty.MEDIUM, QUESTION_SECONDS);
            question.setCreatedAt(quiz.getCreatedAt().plusMillis(quizQuestions.size()));
            quizQuestions.add(question);
        }
        questions.saveAll(quizQuestions);

        jdbc.sql("""
                insert into slide_decks (owner_id, id, quiz_id, title, slides)
                values (:owner, :id, :quiz, :title, cast(:slides as jsonb))
                """).param("owner", owner).param("id", request.id()).param("quiz", quiz.getId()).param("title", title)
                .param("slides", json.writeValueAsString(content.slides())).update();
        return get(owner, request.id());
    }

    private Optional<Deck> find(UUID owner, UUID id) {
        return jdbc.sql("select id, quiz_id, title, slides, created_at from slide_decks where owner_id = :owner and id = :id")
                .param("owner", owner).param("id", id).query((rs, row) -> new Deck(rs.getObject("id", UUID.class),
                        rs.getString("title"), rs.getObject("quiz_id", UUID.class), json.readValue(rs.getString("slides"), SLIDES),
                        rs.getObject("created_at", OffsetDateTime.class).toInstant())).optional();
    }
}
