# LingoArena Backend Design Spec (Spring Boot)

**Date:** 2026-05-28
**Status:** Approved

## Overview

LingoArena is a real-time English word battle game backend. Players create rooms, select wordbooks, and compete in real-time via WebSocket to answer English vocabulary questions. Rebuilt from Python + FastAPI to Java + Spring Boot.

**Changes from Python version (2026-05-25 spec):**
- Tech stack: FastAPI → Spring Boot 3.x + Java 21
- Internal IDs: UUID → Long auto-increment
- Game mode: turn-based only → selectable (turn-based or race)
- Build: pip/uvicorn → Maven
- Object mapping: manual → MapStruct
- Auth framework: manual JWT → Spring Security + JWT

## Tech Stack

| Component       | Technology                         |
|-----------------|------------------------------------|
| JDK             | 21 (LTS)                           |
| Framework       | Spring Boot 3.4.x                  |
| Web             | Spring Web MVC (Tomcat)            |
| ORM             | Spring Data JPA + Hibernate        |
| Database        | PostgreSQL                          |
| Cache/State     | Redis + Spring Data Redis           |
| Realtime        | Spring WebSocket (raw, non-STOMP)  |
| Auth            | Spring Security + JWT (jjwt 3-part) |
| Password        | BCrypt                             |
| Object Mapping  | MapStruct + Lombok                 |
| Build           | Maven                              |
| Package         | com.lingoarena                     |
| Project name    | lingoarena-backend                 |
| Migrations      | Flyway                             |
| Testing DB      | Testcontainers (PostgreSQL)        |

## Project Structure

```
lingoarena-backend/
├── pom.xml
├── docker-compose.yml              # PostgreSQL + Redis (with healthchecks)
├── Dockerfile
├── src/main/java/com/lingoarena/
│   ├── LingoArenaApplication.java          # @SpringBootApplication + @EnableJpaAuditing
│   ├── controller/
│   │   ├── AuthController.java
│   │   ├── WordbookController.java
│   │   ├── RoomController.java
│   │   └── HistoryController.java
│   ├── dto/
│   │   ├── request/
│   │   │   ├── RegisterRequest.java
│   │   │   ├── LoginRequest.java
│   │   │   ├── RefreshTokenRequest.java
│   │   │   ├── CreateRoomRequest.java
│   │   │   └── JoinRoomRequest.java
│   │   ├── response/
│   │   │   ├── AuthResponse.java
│   │   │   ├── RoomResponse.java
│   │   │   ├── GameResultResponse.java
│   │   │   └── StatsResponse.java
│   │   └── websocket/                     # WebSocket 消息类型化 DTO
│   │       ├── WebSocketMessage.java       # 统一消息包装
│   │       ├── NewQuestionMessage.java
│   │       ├── SubmitAnswerMessage.java
│   │       ├── RoundResultMessage.java
│   │       └── GameOverMessage.java
│   ├── entity/                            # JPA 实体（@ManyToOne 懒加载）
│   │   ├── User.java
│   │   ├── Wordbook.java
│   │   ├── Word.java
│   │   ├── GameRoom.java
│   │   ├── GameResult.java
│   │   ├── RoundRecord.java
│   │   └── RefreshToken.java              # 持久化 refresh token
│   ├── repository/
│   │   ├── UserRepository.java
│   │   ├── WordbookRepository.java
│   │   ├── WordRepository.java
│   │   ├── GameRoomRepository.java
│   │   ├── GameResultRepository.java
│   │   ├── RoundRecordRepository.java
│   │   └── RefreshTokenRepository.java
│   ├── service/
│   │   ├── AuthService.java
│   │   ├── WordbookService.java
│   │   ├── RoomService.java
│   │   ├── GameService.java
│   │   ├── HistoryService.java
│   │   └── RoundRecordService.java         # 回合记录持久化
│   ├── websocket/
│   │   ├── RoomWebSocketHandler.java       # WebSocket 消息处理
│   │   ├── WebSocketSessionManager.java    # 连接管理（线程安全 ConcurrentHashMap）
│   │   ├── JwtHandshakeInterceptor.java    # 连接时 JWT 验证
│   │   └── WebSocketConfig.java           # 注册 Handler + Interceptor
│   ├── engine/
│   │   ├── GameManager.java               # 游戏主逻辑、状态机（线程安全）
│   │   ├── QuestionGenerator.java         # 题目生成
│   │   └── ScoringEngine.java             # 计分（原子操作）
│   ├── redis/
│   │   ├── RedisConfig.java
│   │   ├── RoomStateRepository.java       # 房间状态 Hash（lingoarena:room:{id}）
│   │   └── GameStateRepository.java       # 题目队列、答案暂存
│   ├── mapper/                            # MapStruct 映射接口
│   │   ├── UserMapper.java
│   │   ├── WordbookMapper.java
│   │   └── GameRoomMapper.java
│   ├── exception/                         # 全局异常处理
│   │   ├── GlobalExceptionHandler.java    # @RestControllerAdvice
│   │   ├── BusinessException.java         # 自定义运行时异常
│   │   └── ErrorCode.java                 # 错误码枚举
│   ├── config/
│   │   ├── AppConfig.java
│   │   └── JacksonConfig.java             # snake_case 序列化配置
│   └── security/
│       ├── SecurityConfig.java            # Spring Security + CORS + CSRF 禁用
│       ├── JwtTokenService.java           # JWT 生成/验证
│       └── JwtAuthFilter.java             # REST 请求拦截认证
├── src/main/resources/
│   ├── application.yml
│   ├── db/migration/                      # Flyway 迁移脚本
│   │   ├── V1__init_schema.sql            # 建表
│   │   └── V2__seed_wordbooks.sql         # 导入内置词库
│   └── data/                              # 内置词库 JSON（Flyway 使用）
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

- 内部主键统一使用 `Long` 自增 ID（`@GeneratedValue(strategy = IDENTITY)`，注意此策略会禁用 JDBC batch insert，对 MVP 规模无影响）
- 所有外键在 JPA 中以 `@ManyToOne(fetch = LAZY)` 对象关联形式存在，而非原始 Long 字段
- 对外暴露的 Room 通过 `room_code`（6 位字母数字）标识
- `created_at` / `updated_at` 使用 JPA Auditing 自动填充（`@CreatedDate`、`@LastModifiedDate`）

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

**JPA 关联参考：**
```java
@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String passwordHash;
    private String nickname;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
}
```

### refresh_tokens

```sql
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

**JPA 关联参考（所有关联均使用 LAZY）：**
```java
@Entity @Table(name = "words")
public class Word {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wordbook_id")
    private Wordbook wordbook;
    private String english;
    private String chinese;
    private String phonetic;
    private Integer orderIndex;
}
```

### game_rooms

```sql
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
    question_type   VARCHAR(20) NOT NULL,
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

所有 Redis key 加 `lingoarena:` 命名空间前缀，避免与同实例其他服务冲突。

| Redis Key | 类型 | 用途 |
|-----------|------|------|
| `lingoarena:room:{id}` | Hash | 房间状态：status, hostId, guestId, wordbookId, currentRound, totalRounds, hostReady, guestReady, hostScore, guestScore, gameMode |
| `lingoarena:room:{id}:questions` | List | 预生成的题目对象 JSON 列表 |
| `lingoarena:room:{id}:round:{n}:answers` | Hash | 提交的答案：`{userId}` → `{answer, timestampMs}` JSON |

并发安全：写入操作使用 Redis Lua 脚本或 `MULTI`/`EXEC` 事务，确保原子性。

## Maven Dependencies (pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.4.x</version>
</parent>

<properties>
    <java.version>21</java.version>
    <mapstruct.version>1.6.x</mapstruct.version>
    <jjwt.version>0.12.x</jjwt.version>
</properties>

<dependencies>
    <!-- Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-websocket</artifactId>
    </dependency>

    <!-- Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- JPA + PostgreSQL -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>

    <!-- JWT (jjwt 分 3 个 artifact) -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>${jjwt.version}</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>${jjwt.version}</version>
        <scope>runtime</scope>
    </dependency>

    <!-- MapStruct + Lombok -->
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>${mapstruct.version}</version>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Flyway -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <configuration>
                <annotationProcessorPaths>
                    <!-- 顺序重要：Lombok 必须在前，MapStruct 在后 -->
                    <path>
                        <groupId>org.projectlombok</groupId>
                        <artifactId>lombok</artifactId>
                        <version>${lombok.version}</version>
                    </path>
                    <path>
                        <groupId>org.mapstruct</groupId>
                        <artifactId>mapstruct-processor</artifactId>
                        <version>${mapstruct.version}</version>
                    </path>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

## Configuration (application.yml)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lingoarena
    username: lingoarena
    password: lingoarena
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate   # 由 Flyway 管理 DDL，禁用 auto-DDL
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  redis:
    host: localhost
    port: 6379
  jackson:
    property-naming-strategy: SNAKE_CASE   # 所有 JSON 字段使用 snake_case
    serialization:
      write-dates-as-timestamps: false
    date-format: yyyy-MM-dd'T'HH:mm:ss'Z'
    time-zone: UTC
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true

jwt:
  secret: ${JWT_SECRET:change-me-in-production}
  access-token-expiration-minutes: 15
  refresh-token-expiration-days: 7

game:
  round:
    time-limit-seconds: 15

app:
  cors:
    allowed-origins: http://localhost:3000

server:
  port: 8080
```

## REST API

### Auth

| Method | Path               | Request Body                         | Response                                    | Auth |
|--------|--------------------|--------------------------------------|---------------------------------------------|------|
| POST   | /api/auth/register | { email, password, nickname }        | { user, accessToken, refreshToken }          | No   |
| POST   | /api/auth/login    | { email, password }                  | { accessToken, refreshToken }                | No   |
| POST   | /api/auth/refresh  | { refreshToken }                     | { accessToken, refreshToken }                | No   |
| POST   | /api/auth/logout   | { refreshToken }                     | —                                           | Yes  |
| GET    | /api/auth/me       | —                                    | { user }                                    | Yes  |

### Wordbooks

| Method | Path                             | Query Params      | Response                                  | Auth |
|--------|----------------------------------|-------------------|-------------------------------------------|------|
| GET    | /api/wordbooks                   | —                 | { wordbooks: [...] }                      | Yes  |
| GET    | /api/wordbooks/{id}              | —                 | { wordbook }                              | Yes  |
| GET    | /api/wordbooks/{id}/words        | page, size        | { words: [...], total, page, size }        | Yes  |

### Rooms

| Method | Path               | Request Body                                | Response       | Auth |
|--------|--------------------|---------------------------------------------|----------------|------|
| POST   | /api/rooms         | { wordbookId?, totalRounds?, gameMode? }    | { room }       | Yes  |
| POST   | /api/rooms/join    | { roomCode }                                | { room }       | Yes  |
| GET    | /api/rooms/{id}    | —                                           | { room }       | Yes  |

### History

| Method | Path                     | Query Params | Response                                              | Auth |
|--------|--------------------------|-------------|-------------------------------------------------------|------|
| GET    | /api/history/rooms       | page, size  | { rooms: [...], total, page, size }                    | Yes  |
| GET    | /api/history/rooms/{id}  | —           | { room, rounds: [...] }                               | Yes  |
| GET    | /api/stats/me            | —           | { totalGames, wins, losses, avgScore }                 | Yes  |

## WebSocket Protocol

**Endpoint:** `ws://host/ws/room?roomId={roomId}&token={jwt}`

注意：roomId 通过 query parameter 传递，而非 URL path 变量。Spring 原生的 WebSocket 注册不支持 `{roomId}` 路径模板。

连接时经过 `JwtHandshakeInterceptor` 验证 JWT，验证失败返回 401。

统一 JSON 格式: `{ "type": "...", "payload": {...} }`

### 连接生命周期

```
Client connects with JWT
  → JwtHandshakeInterceptor validates token
  → RoomWebSocketHandler.afterConnectionEstablished adds session
  → Server broadcasts room_joined

Client disconnects
  → Server broadcasts opponent_disconnected
  → Server starts reconnection wait (e.g. 30s)

Client reconnects (same roomId + same userId)
  → Server validates token, replaces session
  → Server broadcasts opponent_reconnected
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
      "questionType": "CHOICE",
      "prompt": "放弃，抛弃",
      "options": ["abandon", "abnormal", "abolish", "absolute"],
      "timeLimit": 15
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

### 线程安全

- `WebSocketSessionManager` 使用 `ConcurrentHashMap<String, Set<WebSocketSession>>` 管理房间到连接的映射
- `GameManager` 每个房间持有一个 `ReentrantLock`（或 `synchronized` monitor），所有状态变更在锁内执行
- 计分使用 `AtomicInteger` 或通过房间级锁保护读写
- Redis 状态更新使用 Lua 脚本保证原子性（如检查-设值操作）

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

- 每轮 15 秒计时器（值可通过 `application.yml` 的 `game.round.time-limit-seconds` 配置）
- 使用 `ScheduledExecutorService` + Redis TTL 双重保障
- 超时后自动标记为 wrong
- 双方都完成（答完或超时）→ 发布 round_result

### 结果持久化

- 房间状态和游戏进度在 Redis 中实时管理
- 游戏结束后写入 PostgreSQL（game_results + round_records）
- `RoundRecordService` 负责批量写入回合记录

## Security

| 方面 | 实现 |
|------|------|
| 密码加密 | BCrypt via `PasswordEncoder` |
| JWT 令牌 | jjwt 库，access token 15min，refresh token 7d |
| Refresh 令牌存储 | 持久化到 PostgreSQL `refresh_tokens` 表，支持撤销（`revoked` 字段） |
| REST 认证 | `JwtAuthFilter`（继承 `OncePerRequestFilter`），拦截 `/api/**` |
| 白名单 | `/api/auth/register`, `/api/auth/login` 不拦截 |
| WebSocket 认证 | `JwtHandshakeInterceptor` 在握手阶段验证 token query param |
| 输入校验 | `@Valid` + `@NotBlank`/`@Email`/`@Size` 等注解 |
| CORS | 在 `SecurityConfig` 中通过 `HttpSecurity.cors()` 配置，非独立 `WebMvcConfigurer` |
| 全局异常处理 | `@RestControllerAdvice` 返回统一 JSON 错误 `{ code, message }` |
| 房间授权 | 仅房主可选择词库和开始游戏 |

## Global Exception Handling

采用 `@RestControllerAdvice` 统一错误响应格式：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity.status(e.getHttpStatus())
            .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        // 收集所有字段错误，返回第一个
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .findFirst().orElse("Validation failed");
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        return ResponseEntity.status(500)
            .body(new ErrorResponse("INTERNAL_ERROR", "服务器内部错误"));
    }
}
```

## Wordbook Data (MVP)

内置词库以 JSON 格式存储在 `src/main/resources/data/`，通过 Flyway 迁移脚本导入：

- `cet4.json` — 四级核心词汇 ~500 词
- `cet6.json` — 六级核心词汇 ~500 词
- `kaoyan.json` — 考研核心词汇 ~500 词

词条格式: `{ "english": "abandon", "chinese": "放弃，抛弃", "phonetic": "/əˈbændən/" }`

使用 Flyway migration 脚本（`V2__seed_wordbooks.sql`）读取 JSON 并插入，确保幂等性。

## Testing Strategy

| 层次 | 框架 | 测试内容 |
|------|------|----------|
| Unit | JUnit 5 + Mockito | 游戏引擎（题目生成、计分、超时逻辑） |
| Repository | @DataJpaTest + Testcontainers（真实 PostgreSQL） | JPA 数据访问、查询方法 |
| API | @SpringBootTest + MockMvc | REST 端点请求响应 |
| WebSocket | @SpringBootTest + WebSocket Client | 协议消息流、连接断开重连 |

Repository 测试使用 Testcontainers 而非 H2 内存库，避免因 PostgreSQL 特有行为导致的假阳性。

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
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U lingoarena -d lingoarena"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
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
