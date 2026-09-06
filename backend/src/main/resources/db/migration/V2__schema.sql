-- TIMOVI
-- ==========================================
CREATE TABLE team (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL UNIQUE,
    captain_id BIGINT       NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE team_member (
    team_id BIGINT NOT NULL REFERENCES team (id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    PRIMARY KEY (team_id, user_id)
);

-- KVIZOVI I PITANJA
-- ==========================================
CREATE TABLE quiz (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    creator_id    BIGINT       NOT NULL REFERENCES app_user (id),
    status        VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    time_limit_minutes INTEGER,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_quiz_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED'))
);

CREATE TABLE question (
    id                 BIGSERIAL PRIMARY KEY,
    quiz_id            BIGINT      NOT NULL REFERENCES quiz (id) ON DELETE CASCADE,
    type               VARCHAR(20) NOT NULL,
    text               TEXT        NOT NULL,
    time_limit_seconds INTEGER     NOT NULL DEFAULT 30,
    base_points        INTEGER     NOT NULL DEFAULT 100,
    order_index        INTEGER     NOT NULL,
    CONSTRAINT chk_question_type CHECK (type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'OPEN')),
    CONSTRAINT chk_time_limit    CHECK (time_limit_seconds BETWEEN 5 AND 600),
    CONSTRAINT chk_base_points   CHECK (base_points > 0),
    CONSTRAINT uq_question_order UNIQUE (quiz_id, order_index)
);

CREATE TABLE answer_option (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT  NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    text        VARCHAR(500) NOT NULL,
    is_correct  BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INTEGER NOT NULL
);

CREATE TABLE acceptable_answer (
    id          BIGSERIAL PRIMARY KEY,
    question_id BIGINT       NOT NULL REFERENCES question (id) ON DELETE CASCADE,
    text        VARCHAR(500) NOT NULL
);

-- SUDJELOVANJE I ODGOVORI
-- ==========================================
CREATE TABLE participation (
    id                          BIGSERIAL PRIMARY KEY,
    quiz_id                     BIGINT      NOT NULL REFERENCES quiz (id) ON DELETE CASCADE,
    user_id                     BIGINT      REFERENCES app_user (id),
    team_id                     BIGINT      REFERENCES team (id),
    status                      VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS',
    current_question_index      INTEGER     NOT NULL DEFAULT 0,
    current_question_started_at TIMESTAMP,
    total_score                 INTEGER     NOT NULL DEFAULT 0,
    started_at                  TIMESTAMP   NOT NULL DEFAULT NOW(),
    finished_at                 TIMESTAMP,
    CONSTRAINT chk_participation_status CHECK (status IN ('IN_PROGRESS', 'FINISHED', 'ABANDONED')),
    CONSTRAINT chk_participant_exclusive CHECK (
        (user_id IS NOT NULL AND team_id IS NULL) OR
        (user_id IS NULL AND team_id IS NOT NULL)
    )
);

-- jedan pokušaj po korisniku, odnosno po timu, po kvizu
CREATE UNIQUE INDEX uq_participation_user
    ON participation (quiz_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX uq_participation_team
    ON participation (quiz_id, team_id) WHERE team_id IS NOT NULL;

CREATE TABLE submitted_answer (
    id                 BIGSERIAL PRIMARY KEY,
    participation_id   BIGINT    NOT NULL REFERENCES participation (id) ON DELETE CASCADE,
    question_id        BIGINT    NOT NULL REFERENCES question (id),
    selected_option_id BIGINT    REFERENCES answer_option (id),
    text_answer        VARCHAR(500),
    is_correct         BOOLEAN   NOT NULL,
    time_taken_ms      BIGINT    NOT NULL,
    points_awarded     INTEGER   NOT NULL,
    answered_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_answer_per_question UNIQUE (participation_id, question_id)
);

-- indeksi za česte upite
CREATE INDEX idx_question_quiz       ON question (quiz_id);
CREATE INDEX idx_participation_quiz  ON participation (quiz_id);
CREATE INDEX idx_answer_participation ON submitted_answer (participation_id);