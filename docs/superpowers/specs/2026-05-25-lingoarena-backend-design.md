# LingoArena Backend Design Spec

**Date:** 2026-05-25
**Status:** Draft

## Overview

LingoArena is a real-time English word/sentence battle game backend. Players create rooms, select wordbooks, and compete in real-time via WebSocket to answer English vocabulary questions. Built with Python + FastAPI, targeting Chinese users with built-in wordbooks aligned to domestic English exam standards (CET-4, CET-6, postgraduate, IELTS, TOEFL).

## High-Level Architecture

```
Client (Web) ──HTTP──→ FastAPI REST API ──→ PostgreSQL (persistent)
             ──WS────→ FastAPI WebSocket ──→ Redis (real-time state)
```

**Separation of concerns:**
- **PostgreSQL** stores persistent data: users, wordbooks, words, game results, round records
- **Redis** manages real-time state: room sessions, active game state, question queues, timers
- Game results are written to PostgreSQL after each match finishes

## Tech Stack

| Component       | Technology                         |
|----------------|------------------------------------|
| Web framework  | FastAPI                            |
| ORM            | SQLAlchemy 2.0 (async) + asyncpg   |
| Realtime       | FastAPI WebSocket                  |
| Cache/State    | Redis                              |
| Database       | PostgreSQL                         |
| Migrations     | Alembic                            |
| Validation     | Pydantic v2                        |
| Auth           | JWT (access + refresh tokens)      |
| Password       | passlib + bcrypt                   |

## Data Model

### PostgreSQL Tables

**users**
- `id` (UUID, PK)
- `email` (varchar, unique, indexed)
- `password_hash` (varchar)
- `nickname` (varchar)
- `created_at` (timestamptz)
- `updated_at` (timestamptz)

**wordbooks**
- `id` (UUID, PK)
- `name` (varchar) — e.g. "四级核心词汇"
- `description` (text)
- `level` (varchar) — enum: CET4, CET6, KAOYAN, IELTS, TOEFL
- `is_active` (boolean)
- `created_at` (timestamptz)

**words**
- `id` (UUID, PK)
- `wordbook_id` (FK → wordbooks, indexed)
- `english` (varchar)
- `chinese` (varchar)
- `phonetic` (varchar, nullable)
- `order_index` (integer)

**game_rooms**
- `id` (UUID, PK)
- `room_code` (varchar(6), unique, indexed) — 6-char alphanumeric join code
- `host_id` (FK → users)
- `guest_id` (FK → users, nullable)
- `wordbook_id` (FK → wordbooks, nullable)
- `status` (varchar) — enum: waiting, playing, finished, cancelled
- `total_rounds` (integer)
- `winner_id` (FK → users, nullable)
- `host_score` (integer)
- `guest_score` (integer)
- `created_at`, `started_at`, `finished_at` (timestamptz)

**game_results** (per-player per-game summary)
- `id` (UUID, PK)
- `room_id` (FK → game_rooms)
- `user_id` (FK → users)
- `score` (integer)
- `correct_count` (integer)
- `wrong_count` (integer)
- `total_questions` (integer)
- `created_at` (timestamptz)

**round_records** (per-round per-player detail)
- `id` (UUID, PK)
- `room_id` (FK → game_rooms, indexed)
- `round_number` (integer)
- `user_id` (FK → users)
- `word_id` (FK → words)
- `question_type` (varchar) — enum: spell, choice
- `user_answer` (varchar)
- `is_correct` (boolean)
- `time_spent_ms` (integer)
- `created_at` (timestamptz)

### Redis Data Structures

**Room state:** `room:{id}` → Hash
- status, host_id, guest_id, wordbook_id, current_round, total_rounds
- host_ready (bool), guest_ready (bool)
- host_score, guest_score

**Question queue:** `room:{id}:questions` → List
- Pre-generated question objects for the entire game

**Submitted answers:** `room:{id}:round:{n}:answers` → Hash
- `{user_id}` → `{answer, timestamp_ms}`

## REST API

### Auth
| Method | Path               | Description        |
|--------|-------------------|--------------------|
| POST   | /api/auth/register | Email registration |
| POST   | /api/auth/login    | Login, returns JWT |
| POST   | /api/auth/refresh  | Refresh token      |
| GET    | /api/auth/me       | Current user info  |

### Wordbooks
| Method | Path                              | Description              |
|--------|-----------------------------------|--------------------------|
| GET    | /api/wordbooks                    | List all wordbooks       |
| GET    | /api/wordbooks/{id}               | Wordbook detail          |
| GET    | /api/wordbooks/{id}/words         | Paginated word list      |

### Rooms
| Method | Path               | Description                    |
|--------|-------------------|--------------------------------|
| POST   | /api/rooms         | Create room, returns room_code |
| POST   | /api/rooms/join    | Join room by room_code         |
| GET    | /api/rooms/{id}    | Get room info                  |

### History
| Method | Path                     | Description              |
|--------|--------------------------|--------------------------|
| GET    | /api/history/rooms       | My battle history        |
| GET    | /api/history/rooms/{id}  | Single game detail       |
| GET    | /api/stats/me            | Personal statistics      |

## WebSocket Protocol

**Endpoint:** `ws://host/ws/room/{room_id}?token={jwt}`

All messages use unified JSON format: `{ "type": "...", "payload": {...} }`

### Room Phase
```
Client → Server:
  { type: "player_ready" }
  { type: "select_wordbook", payload: { wordbook_id } }  (host only)

Server → Client:
  { type: "room_joined", payload: { user } }
  { type: "player_ready_ack", payload: { user_id, ready: bool } }
  { type: "game_start", payload: { total_rounds, total_questions } }
```

### Game Phase
```
Server → Client:
  { type: "new_question", payload: {
      round, question_type, prompt,
      options (choice only, array of 4, shuffled),
      time_limit (seconds) } }

Client → Server:
  { type: "submit_answer", payload: { round, answer } }

Server → Client (both answered or timeout):
  { type: "round_result", payload: {
      round, correct_answer,
      results: [{ user_id, answer, is_correct, time_spent_ms }],
      scores: [{ user_id, score }] } }

Server → Client (game ended):
  { type: "game_over", payload: {
      winner_id, final_scores,
      summary: { total_questions, correct_count, wrong_count } } }
```

### Error / Disconnect
```
Server → Client:
  { type: "error", payload: { code, message } }
  { type: "opponent_disconnected", payload: { user_id } }
  { type: "opponent_reconnected", payload: { user_id } }
```

## Game Engine

### State Machine
```
WAITING → READY → PLAYING → FINISHED
                    ↕ (per round)
             ROUND_ACTIVE → (timeout) → auto fail
                    ↓ both answered
             ROUND_RESULT → next round
```

### Question Generation
- At game start, pre-generate all questions and push to Redis List
- Randomly sample N words from the chosen wordbook (no repeats within one game)
- Each word randomly assigned a question type (spell or choice)
- Choice questions: 3 distractors randomly selected from same wordbook
- Total questions = total_rounds (each player answers 1 question per round)

### Game Flow (MVP)
- Total rounds: 10 (10 questions per player)
- Players alternate: Player A → Player B → Player A → ...
- Each question: 15-second time limit
- Scoring: correct = +10, wrong/timeout = 0
- After both players finish or timeout, results are broadcast immediately
- Result persistence happens after game_over

### Timeout Management
- `asyncio.create_task` timer per question per player
- 15s timeout → auto-mark as wrong → trigger round_result if opponent already answered
- Prevents griefing by stalling

## Project Structure

```
lingoarena-backend/
├── app/
│   ├── main.py                    # FastAPI entry, WebSocket mount
│   ├── api/                       # REST routers
│   │   ├── auth.py, users.py, wordbooks.py, rooms.py, history.py
│   ├── ws/                        # WebSocket handlers
│   │   ├── room_handler.py, game_handler.py
│   ├── services/                  # Business logic
│   │   ├── auth_service.py, room_service.py,
│   │   ├── game_service.py, wordbook_service.py
│   ├── engine/                    # Game core engine
│   │   ├── game_manager.py, question_generator.py
│   │   ├── scoring.py, timer.py
│   ├── models/                    # SQLAlchemy models
│   │   ├── user.py, wordbook.py, word.py,
│   │   ├── game_room.py, game_result.py, round_record.py
│   ├── schemas/                   # Pydantic schemas
│   │   ├── auth.py, room.py, game.py, wordbook.py
│   ├── redis/                     # Redis operations
│   │   ├── client.py, room_state.py, game_state.py
│   └── core/                      # Infrastructure
│       ├── config.py, database.py, security.py, exceptions.py
├── data/                          # Built-in wordbooks (JSON)
│   ├── cet4.json, cet6.json, kaoyan.json
├── migrations/                    # Alembic migrations
├── tests/                         # Tests
│   ├── conftest.py, test_auth.py, test_game_engine.py, test_ws_protocol.py
├── requirements.txt
├── docker-compose.yml             # PostgreSQL + Redis
├── Dockerfile
└── alembic.ini
```

## Wordbook Data (MVP)

Built-in wordbooks shipped as JSON files in `data/`:
- **CET-4:** ~500 core vocabulary words
- **CET-6:** ~500 core vocabulary words
- **考研 (Postgraduate):** ~500 core vocabulary words

Each word entry: `{ "english": "abandon", "chinese": "放弃，抛弃", "phonetic": "/əˈbændən/" }`

Seeding: on first server start, a migration/script loads these into PostgreSQL.

## Security
- Passwords: bcrypt hashing via passlib
- Auth: JWT access tokens (short-lived, 15min) + refresh tokens (7-day)
- WebSocket: token validated on connection via query parameter
- Input validation: Pydantic on all REST endpoints
- Rate limiting: future consideration

## Testing Strategy
- Unit tests: game engine (question generation, scoring, timer logic)
- Integration tests: WebSocket protocol message flow
- API tests: REST endpoint CRUD
- Fixtures: test database, test Redis, mock wordbooks

## Future Considerations (Post-MVP)
- Social login (WeChat)
- User-created wordbooks
- More question types (listening, sentence ordering)
- Ranking/leaderboard
- Matchmaking (auto queue instead of room code)
- Spectator mode
- Streak bonuses in scoring
