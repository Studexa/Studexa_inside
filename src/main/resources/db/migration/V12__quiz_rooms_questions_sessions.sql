-- Quiz rooms (quizzes) created by hosts
CREATE TABLE quizzes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    subject VARCHAR(100),
    visibility VARCHAR(20) NOT NULL DEFAULT 'private', -- private, public
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_quizzes_owner_id ON quizzes(owner_id);
CREATE INDEX idx_quizzes_subject ON quizzes(subject);

-- Quiz questions belonging to a quiz
CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'multiple_choice', -- multiple_choice, true_false, short_answer
    options TEXT, -- for multiple_choice: JSON array of {id, text}
    correct_answer TEXT NOT NULL,
    explanation TEXT,
    subject VARCHAR(100),
    topic VARCHAR(100),
    subtopic VARCHAR(100),
    difficulty VARCHAR(20) DEFAULT 'medium', -- easy, medium, hard
    time_limit_seconds INTEGER DEFAULT 30,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_questions_quiz_id ON questions(quiz_id);
CREATE INDEX idx_questions_subject_topic ON questions(subject, topic);

-- Live game sessions (rooms)
CREATE TABLE sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
    host_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_code VARCHAR(6) NOT NULL UNIQUE,
    mode VARCHAR(20) NOT NULL DEFAULT 'live_race', -- live_race, team, co_op, self_paced, solo_practice
    status VARCHAR(20) NOT NULL DEFAULT 'waiting', -- waiting, active, ended
    settings TEXT NOT NULL DEFAULT '{}', -- num_questions, time_per_question, difficulty, etc.
    started_at TIMESTAMPTZ,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_room_code ON sessions(room_code);
CREATE INDEX idx_sessions_host_id ON sessions(host_id);
CREATE INDEX idx_sessions_status ON sessions(status);

-- Players in a session (guests allowed - user_id nullable)
CREATE TABLE session_players (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id UUID NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL, -- nullable for guests
    nickname VARCHAR(50) NOT NULL,
    avatar_url VARCHAR(500),
    team_id VARCHAR(20), -- for team mode
    total_score INTEGER NOT NULL DEFAULT 0,
    final_rank INTEGER,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at TIMESTAMPTZ,
    is_ready BOOLEAN NOT NULL DEFAULT false,
    is_host BOOLEAN NOT NULL DEFAULT false
);

CREATE INDEX idx_session_players_session_id ON session_players(session_id);
CREATE INDEX idx_session_players_user_id ON session_players(user_id);
CREATE UNIQUE INDEX idx_session_players_unique_guest ON session_players(session_id, nickname)
    WHERE user_id IS NULL;

-- Answers given by players during a session
CREATE TABLE answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_player_id UUID NOT NULL REFERENCES session_players(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    selected_answer TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL,
    response_time_ms INTEGER NOT NULL,
    score_awarded INTEGER NOT NULL DEFAULT 0,
    answered_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_answers_session_player_id ON answers(session_player_id);
CREATE INDEX idx_answers_question_id ON answers(question_id);

-- Per-user topic weakness stats (cached for dashboard)
CREATE TABLE topic_stats (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    subject VARCHAR(100) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    correct INTEGER NOT NULL DEFAULT 0,
    avg_time_ms INTEGER NOT NULL DEFAULT 0,
    weakness_score DOUBLE PRECISION NOT NULL DEFAULT 1.0, -- 1 - weighted accuracy
    last_updated TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, subject, topic)
);
