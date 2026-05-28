CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    nickname        VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);

CREATE TABLE refresh_tokens (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    token           VARCHAR(500) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);

CREATE TABLE wordbooks (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    level           VARCHAR(20) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE words (
    id              BIGSERIAL PRIMARY KEY,
    wordbook_id     BIGINT NOT NULL REFERENCES wordbooks(id),
    english         VARCHAR(255) NOT NULL,
    chinese         VARCHAR(500) NOT NULL,
    phonetic        VARCHAR(100),
    order_index     INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_words_wordbook_id ON words(wordbook_id);

CREATE TABLE game_rooms (
    id              BIGSERIAL PRIMARY KEY,
    room_code       VARCHAR(6) NOT NULL UNIQUE,
    host_id         BIGINT NOT NULL REFERENCES users(id),
    guest_id        BIGINT REFERENCES users(id),
    wordbook_id     BIGINT REFERENCES wordbooks(id),
    game_mode       VARCHAR(20) NOT NULL DEFAULT 'TURN_BASED',
    status          VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    total_rounds    INT NOT NULL DEFAULT 10,
    winner_id       BIGINT REFERENCES users(id),
    host_score      INT NOT NULL DEFAULT 0,
    guest_score     INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);
CREATE INDEX idx_game_rooms_room_code ON game_rooms(room_code);
CREATE INDEX idx_game_rooms_host_id ON game_rooms(host_id);

CREATE TABLE game_results (
    id              BIGSERIAL PRIMARY KEY,
    room_id         BIGINT NOT NULL REFERENCES game_rooms(id),
    user_id         BIGINT NOT NULL REFERENCES users(id),
    score           INT NOT NULL DEFAULT 0,
    correct_count   INT NOT NULL DEFAULT 0,
    wrong_count     INT NOT NULL DEFAULT 0,
    total_questions INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE round_records (
    id              BIGSERIAL PRIMARY KEY,
    room_id         BIGINT NOT NULL REFERENCES game_rooms(id),
    round_number    INT NOT NULL,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    word_id         BIGINT NOT NULL REFERENCES words(id),
    question_type   VARCHAR(20) NOT NULL,
    user_answer     VARCHAR(500),
    is_correct      BOOLEAN NOT NULL DEFAULT FALSE,
    time_spent_ms   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_round_records_room_id ON round_records(room_id);
