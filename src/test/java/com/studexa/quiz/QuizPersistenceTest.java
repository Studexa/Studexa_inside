package com.studexa.quiz;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import com.studexa.user.User;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class QuizPersistenceTest {
    @Autowired
    private EntityManager entities;

    @Test
    void jsonTextOptionsAndSettingsRoundTripWithoutDoubleEncoding() {
        User owner = User.withPassword("quiz-" + UUID.randomUUID() + "@example.com", "hash");
        entities.persist(owner);
        Quiz quiz = new Quiz(owner.getId(), "Persistence check", null, null, Quiz.Visibility.PRIVATE);
        entities.persist(quiz);
        String options = "[{\"id\":\"a\",\"text\":\"ចម្លើយ\"},{\"id\":\"b\",\"text\":\"Other\"}]";
        Question question = new Question(quiz.getId(), "Choose", Question.Type.MULTIPLE_CHOICE,
                options, "a", null, null, null, null, Question.Difficulty.EASY, 30);
        entities.persist(question);
        String settings = "{\"timePerQuestion\":30}";
        Session session = new Session(quiz.getId(), owner.getId(), "ABC234", Session.Mode.LIVE_RACE, settings);
        entities.persist(session);
        entities.flush();
        entities.clear();
        assertThat(entities.find(Question.class, question.getId()).getOptions()).isEqualTo(options);
        assertThat(entities.find(Session.class, session.getId()).getSettings()).isEqualTo(settings);
    }
}
