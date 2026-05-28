# LingoArena Backend Design Spec (Spring Boot)

**Date:** 2026-05-28
**Status:** Approved

## Overview

LingoArena is a real-time English word battle game backend. Players create rooms, select wordbooks, and compete in real-time via WebSocket to answer English vocabulary questions. Rebuilt from Python + FastAPI to Java + Spring Boot.

**Changes from Python version (2026-05-25 spec):**
- Tech stack: FastAPI → Spring Boot 3.x + Java 17
- Internal IDs: UUID → Long auto-increment
- Game mode: turn-based only → selectable (turn-based or race)
- Build: pip/uvicorn → Maven
- Object mapping: manual → MapStruct
- Auth framework: manual JWT → Spring Security + JWT

## Tech Stack

| Component       | Technology                         |
|-----------------|------------------------------------|
| JDK             | 17 (LTS)                           |
| Framework       | Spring Boot 3.4.x                  |
| Web             | Spring Web MVC (Tomcat)            |
| ORM             | Spring Data JPA + Hibernate        |
| Database        | PostgreSQL                          |
| Cache/State     | Redis + Spring Data Redis           |
| Realtime        | Spring WebSocket (raw, non-STOMP)  |
| Auth            | Spring Security + JWT (jjwt)       |
| Password        | BCrypt                             |
| Object Mapping  | MapStruct + Lombok                 |
| Build           | Maven                              |
| Package         | com.lingoarena                     |
| Project name    | lingoarena-backend                 |

## Project Structure

```
lingoarena-backend/
├── pom.xml
├── docker-compose.yml              # PostgreSQL + Redis
├── Dockerfile
├── src/main/java/com/lingoarena/
│   ├── LingoArenaApplication.java
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── WordbookController.java
│   │   ├── RoomController.java
│   │   └── HistoryController.java
│   ├── dto/
│   │   ├── request/                # 请求 DTO
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── CreateRoomRequest.java
│   │   │   └── JoinRoomRequest.java
│   │   └── response/               # 响应 DTO
│   │       ├── AuthResponse.java
│   │       ├── RoomResponse.java
│   │       └── GameResultResponse.java
│   ├── entity/                     # JPA 实体
│   │   ├── User.java
│   │   ├── Wordbook.java
│   │   ├── Word.java
│   │   ├── GameRoom.java
│   │   ├── GameResult.java
│   │   └── RoundRecord.java
│   ├── repository/                 # Spring Data JPA repositories
│   │   ├── UserRepository.java
│   │   ├── WordbookRepository.java
│   │   ├── WordRepository.java
│   │   ├── GameRoomRepository.java
│   │   ├── GameResultRepository.java
│   │   └── RoundRecordRepository.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── WordbookService.java
│   │   ├── RoomService.java
│   │   ├── GameService.java
│   │   └── HistoryService.java
│   ├── websocket/
│   │   ├── RoomWebSocketHandler.java   # WebSocket 入口
│   │   ├── WebSocketSessionManager.java # 连接管理
│   │   └── WebSocketConfig.java        # 注册 Handler
│   ├── engine/
│   │   ├── GameManager.java            # 游戏主逻辑、状态机
│   │   ├── QuestionGenerator.java       # 题目生成
│   │   └── ScoringEngine.java          # 计分
│   ├── redis/
│   │   ├── RedisConfig.java
│   │   ├── RoomStateRepository.java    # 房间状态 Hash
│   │   └── GameStateRepository.java    # 题目队列、答案暂存
│   ├── mapper/                        # MapStruct 映射接口
│   │   ├── UserMapper.java
│   │   ├── WordbookMapper.java
│   │   └── GameRoomMapper.java
│   ├── config/
│   │   ├── AppConfig.java
│   │   └── CorsConfig.java
│   └── security/
│       ├── SecurityConfig.java        # Spring Security 配置
│       ├── JwtTokenProvider.java      # JWT 生成/验证
│       └── JwtAuthFilter.java         # 请求拦截认证
├── src/main/resources/
│   ├── application.yml
│   └── data/                          # 内置词库 JSON
│       ├── cet4.json
│       ├── cet6.json
│       └── kaoyan.json
└── src/test/java/com/lingoarena/
    ├── engine/
    │   ├── QuestionGeneratorTest.java
    │   └── ScoringEngineTest.java
    ├── controller/
    │   ├── AuthControllerTest.java
    │   └── WordbookControllerTest.java
    ├── websocket/
    │   └── GameWebSocketTest.java
    └── service/
        ├── RoomServiceTest.java
        └── GameServiceTest.java
```

## Database Schema

### 设计原则

- 内部主键统一使用 `Long` 自增 ID
- 对外暴露的 Room 通过 `room_code`（6 位字母数字）标识
- 所有时间字段使用 `LocalDateTime`

### users

```sql
CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    nickname        VARCHAR(100) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_users_email ON users(email);
```

### wordbooks

```sql
CREATE TABLE wordbooks (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    description     TEXT,
    level           VARCHAR(20) NOT NULL,  -- CET4, CET6, KAOYAN, IELTS, TOEFL
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
```

### words

```sql
CREATE TABLE words (
    id              BIGSERIAL PRIMARY KEY,
    wordbook_id     BIGINT NOT NULL REFERENCES wordbooks(id),
    english         VARCHAR(255) NOT NULL,
    chinese         VARCHAR(500) NOT NULL,
    phonetic        VARCHAR(100),
    order_index     INT NOT NULL DEFAULT 0
);
CREATE INDEX idx_words_wordbook_id ON words(wordbook_id);
```

### game_rooms

```sql
CREATE TABLE game_rooms (
    id              BIGSERIAL PRIMARY KEY,
    room_code       VARCHAR(6) NOT NULL UNIQUE,
    host_id         BIGINT NOT NULL REFERENCES users(id),
    guest_id        BIGINT REFERENCES users(id),     -- nullable until joined
    wordbook_id     BIGINT REFERENCES wordbooks(id), -- nullable until selected
    game_mode       VARCHAR(20) NOT NULL DEFAULT 'TURN_BASED', -- TURN_BASED, RACE
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
```

### game_results

```sql
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
```

### round_records

```sql
CREATE TABLE round_records (
    id              BIGSERIAL PRIMARY KEY,
    room_id         BIGINT NOT NULL REFERENCES game_rooms(id),
    round_number    INT NOT NULL,
    user_id         BIGINT NOT NULL REFERENCES users(id),
    word_id         BIGINT NOT NULL REFERENCES words(id),
    question_type   VARCHAR(20) NOT NULL, -- SPELL, CHOICE
    user_answer     VARCHAR(500),
    is_correct      BOOLEAN NOT NULL DEFAULT FALSE,
    time_spent_ms   INT NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_round_records_room_id ON round_records(room_id);
```

### Enums (Java)

```java
public enum WordbookLevel { CET4, CET6, KAOYAN, IELTS, TOEFL }
public enum RoomStatus { WAITING, PLAYING, FINISHED, CANCELLED }
public enum GameMode { TURN_BASED, RACE }
public enum QuestionType { SPELL, CHOICE }
```

## Redis Data Structures

沿用 Python 设计：

**Room state:** `room:{id}` → Hash
- status, hostId, guestId, wordbookId, currentRound, totalRounds
- hostReady, guestReady
- hostScore, guestScore
- gameMode

**Question queue:** `room:{id}:questions` → List
- 预生成的题目对象 JSON 列表

**Submitted answers:** `room:{id}:round:{n}:answers` → Hash
- `{userId}` → `{answer, timestampMs}` JSON

## REST API

### Auth

| Method | Path               | Request Body             | Response              | Auth |
|--------|--------------------|--------------------------|-----------------------|------|
| POST   | /api/auth/register | { email, password, nickname } | { user, accessToken, refreshToken } | No |
| POST   | /api/auth/login    | { email, password }       | { accessToken, refreshToken } | No |
| POST   | /api/auth/refresh  | { refreshToken }         | { accessToken, refreshToken } | No |
| GET    | /api/auth/me       | —                        | { user }              | Yes |

### Wordbooks

| Method | Path                             | Query Params      | Response             | Auth |
|--------|----------------------------------|-------------------|----------------------|------|
| GET    | /api/wordbooks                   | —                 | { wordbooks: [...] } | Yes |
| GET    | /api/wordbooks/{id}              | —                 | { wordbook }         | Yes |
| GET    | /api/wordbooks/{id}/words        | page, size        | { words, total, page, size } | Yes |

### Rooms

| Method | Path               | Request Body             | Response          | Auth |
|--------|--------------------|--------------------------|-------------------|------|
| POST   | /api/rooms         | { wordbookId?, totalRounds?, gameMode? } | { room } | Yes |
| POST   | /api/rooms/join    | { roomCode }             | { room }          | Yes |
| GET    | /api/rooms/{id}    | —                        | { room }          | Yes |

### History

| Method | Path                     | Query Params | Response | Auth |
|--------|--------------------------|-------------|---------|------|
| GET    | /api/history/rooms       | page, size  | { rooms, total, page, size } | Yes |
| GET    | /api/history/rooms/{id}  | —           | { room, rounds: [...] } | Yes |
| GET    | /api/stats/me            | —           | { totalGames, wins, losses, avgScore } | Yes |

## WebSocket Protocol

**Endpoint:** `ws://host/ws/room/{roomId}?token={jwt}`

统一 JSON 格式: `{ "type": "...", "payload": {...} }`

### 连接生命周期

```
Client connects with JWT → Server validates token → Server adds to room session
Client disconnects → Server broadcasts opponent_disconnected
Client reconnects → Server broadcasts opponent_reconnected
```

### Room Phase

```json
Client → Server:
  { "type": "player_ready" }
  { "type": "select_wordbook", "payload": { "wordbookId": 1 } }  // 仅房主

Server → Client:
  { "type": "room_joined", "payload": { "user": {...} } }
  { "type": "player_ready_ack", "payload": { "userId": 1, "ready": true } }
  { "type": "game_start", "payload": { "totalRounds": 10, "gameMode": "RACE" } }
```

### Game Phase

```json
// 服务器发送题目
Server → Client:
  {
    "type": "new_question",
    "payload": {
      "round": 1,
      "questionType": "CHOICE",       // SPELL 或 CHOICE
      "prompt": "放弃，抛弃",
      "options": ["abandon", "abnormal", "abolish", "absolute"],  // choice 时才有
      "timeLimit": 15                  // 秒
    }
  }

// 客户端提交答案
Client → Server:
  { "type": "submit_answer", "payload": { "round": 1, "answer": "abandon" } }

// 双方都提交或超时后，服务器广播结果
Server → Client:
  {
    "type": "round_result",
    "payload": {
      "round": 1,
      "correctAnswer": "abandon",
      "results": [
        { "userId": 1, "answer": "abandon", "isCorrect": true, "timeSpentMs": 3200 },
        { "userId": 2, "answer": "abnormal", "isCorrect": false, "timeSpentMs": 5100 }
      ],
      "scores": [
        { "userId": 1, "score": 10 },
        { "userId": 2, "score": 0 }
      ]
    }
  }

// 游戏结束
Server → Client:
  {
    "type": "game_over",
    "payload": {
      "winnerId": 1,
      "finalScores": [
        { "userId": 1, "score": 80 },
        { "userId": 2, "score": 50 }
      ],
      "summary": {
        "totalQuestions": 10,
        "correctCount": 8,
        "wrongCount": 2
      }
    }
  }
```

### Error / Disconnect

```json
Server → Client:
  { "type": "error", "payload": { "code": "ROOM_FULL", "message": "房间已满" } }
  { "type": "opponent_disconnected", "payload": { "userId": 1 } }
  { "type": "opponent_reconnected", "payload": { "userId": 1 } }
```

## Game Engine

### 状态机

```
WAITING ──(双方 ready)──→ READY ──(host 选词库)──→ PLAYING ──(所有轮次完成)──→ FINISHED
                                                       ↕ (每轮)
                                               ROUND_ACTIVE → (15s 超时) → auto fail
                                                       ↓ 双方都提交或超时
                                               ROUND_RESULT → 下一轮
```

### 题目生成器（QuestionGenerator）

- 游戏开始时预生成所有题目，推入 Redis List
- 从选中词库随机抽取 N 个单词（N = totalRounds），不重复
- 每个单词随机分配题型（SPELL / CHOICE），50% 概率
- CHOICE 题型：从同一词库随机选取 3 个干扰词
- 干扰词与正确答案不重复

### 游戏模式

**TURN_BASED（回合制）：**
- 每轮出 2 道题，每人各 1 题（题目不同）
- 总题数 = totalRounds × 2（每人各答 totalRounds 题）
- 流程：A 答第 1 题 → B 答第 2 题 → 公布两人结果 → 下一轮

**RACE（抢答制）：**
- 每轮出 1 道题，两人同时作答
- 总题数 = totalRounds
- 两人独立提交答案，互不影响
- 双方都提交（或超时）后一起公布结果

### 评分

两种模式统一规则：

| 结果 | 得分 |
|------|------|
| 答对 | +10 |
| 答错 | 0 |
| 超时未答 | 0 |

### 超时管理

- 每轮 15 秒计时器
- 使用 `ScheduledExecutorService` + Redis TTL 实现
- 超时后自动标记为 wrong
- 双方都完成（答完或超时）→ 发布 round_result

### 结果持久化

- 房间状态和游戏进度在 Redis 中实时管理
- 游戏结束后写入 PostgreSQL（game_results + round_records）

## Security

| 方面 | 实现 |
|------|------|
| 密码加密 | BCrypt via `PasswordEncoder` |
| JWT 令牌 | jjwt 库，access token 15min，refresh token 7d |
| REST 认证 | Spring Security Filter Chain，拦截 `/api/**` |
| 白名单 | `/api/auth/register`, `/api/auth/login` 不拦截 |
| WebSocket 认证 | 连接时从 query param `token` 解析并验证 JWT |
| 输入校验 | `@Valid` + `@NotBlank`/`@Email`/`@Size` 等注解 |
| CORS | `@Configuration` 配置允许的前端地址 |
| 房间授权 | 仅房主可选择词库和开始游戏 |

## Wordbook Data (MVP)

内置词库以 JSON 格式存储在 `src/main/resources/data/`：

- `cet4.json` — 四级核心词汇 ~500 词
- `cet6.json` — 六级核心词汇 ~500 词
- `kaoyan.json` — 考研核心词汇 ~500 词

词条格式: `{ "english": "abandon", "chinese": "放弃，抛弃", "phonetic": "/əˈbændən/" }`

首次启动时通过 `CommandLineRunner` 或 Flyway 脚本导入 PostgreSQL。

## Testing Strategy

| 层次 | 框架 | 测试内容 |
|------|------|----------|
| Unit | JUnit 5 + Mockito | 游戏引擎（题目生成、计分、超时逻辑） |
| Repository | @DataJpaTest + H2 | JPA 数据访问、查询方法 |
| API | @SpringBootTest + MockMvc | REST 端点请求响应 |
| WebSocket | @SpringBootTest + WebSocket Client | 协议消息流、连接断开重连 |

## Docker

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: lingoarena
      POSTGRES_USER: lingoarena
      POSTGRES_PASSWORD: lingoarena
    ports:
      - "5432:5432"

  redis:
    image: redis:7
    ports:
      - "6379:6379"

  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - postgres
      - redis
```

## Future Considerations (Post-MVP)

未进入当前 MVP 范围，但设计预留了扩展空间：
- 社交登录（微信）
- 用户自建词库
- 更多题型（听力、排序）
- 排行榜
- 自动匹配（替代房间码）
- 观战模式
- 连胜加分
- Spring Boot Actuator + 监控
