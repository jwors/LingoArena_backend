# LingoArena API 接口文档

> 基于 Spring Boot 3.4 + WebSocket 的实时对战单词平台

---

## 目录

1. [基础信息](#1-基础信息)
2. [认证接口](#2-认证接口-api-auth)
3. [用户接口](#3-用户接口-api-users)
4. [房间接口](#4-房间接口-api-rooms)
5. [词库接口](#5-词库接口-api-wordbooks)
6. [历史与统计接口](#6-历史与统计接口-api-history--api-stats)
7. [WebSocket 接口](#7-websocket-接口-ws-room)
8. [全局错误响应](#8-全局错误响应)

---

## 1. 基础信息

| 项目 | 值 |
|------|-----|
| **Base URL** | `http://localhost:8080` |
| **WebSocket URL** | `ws://localhost:8080/ws/room` |
| **JSON 命名策略** | 驼峰转蛇形（如 `accessToken` → `access_token`） |
| **时间格式** | ISO 8601 UTC（`yyyy-MM-dd'T'HH:mm:ss'Z'`） |

### 通用请求头

```
Content-Type: application/json
Authorization: Bearer <jwt_token>  (需要认证的接口)
```

---

## 2. 认证接口 `/api/auth`

### 2.1 注册

```
POST /api/auth/register
```

**Request Body:**

```json
{
  "email": "user@example.com",
  "password": "password123",
  "nickname": "玩家Nick"
}
```

| 参数 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `email` | String | 是 | 合法邮箱格式 |
| `password` | String | 是 | 最小 6 位 |
| `nickname` | String | 是 | 最长 50 字符 |

**Response `200`:**

```json
{
  "access_token": "eyJhbGciOiJIUzI1NiJ9...",
  "refresh_token": "dGhpcyBpcyBhIHJlZnJl...",
  "user": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "玩家Nick"
  }
}
```

---

### 2.2 登录

```
POST /api/auth/login
```

**Request Body:**

```json
{
  "email": "user@example.com",
  "password": "password123"
}
```

| 参数 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `email` | String | 是 | |
| `password` | String | 是 | |

**Response `200`:** 同注册返回格式（`AuthResponse`）。

---

### 2.3 刷新令牌

```
POST /api/auth/refresh
```

**Request Body:**

```json
{
  "refresh_token": "dGhpcyBpcyBhIHJlZnJl..."
}
```

| 参数 | 类型 | 必填 |
|------|------|------|
| `refresh_token` | String | 是 |

**Response `200`:** 返回新的 `access_token` + `refresh_token` + `user`。

---

### 2.4 登出

```
POST /api/auth/logout
```

**Request Body:**

```json
{
  "refresh_token": "dGhpcyBpcyBhIHJlZnJl..."
}
```

**Response `200`:** 空 body。

---

### 2.5 获取当前用户信息

```
GET /api/auth/me
```

**Headers:** `Authorization: Bearer <token>`

**Response `200`:**

```json
{
  "id": 1,
  "email": "user@example.com",
  "nickname": "玩家Nick"
}
```

---

## 3. 用户接口 `/api/users`

### 3.1 获取当前用户信息

```
GET /api/users/me
```

**Headers:** `Authorization: Bearer <token>`

**Response `200`:**

```json
{
  "id": 1,
  "email": "user@example.com",
  "nickname": "玩家Nick"
}
```

---

### 3.2 获取指定用户信息

```
GET /api/users/{id}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 用户 ID |

**Response `200`:**

```json
{
  "id": 1,
  "nickname": "玩家Nick"
}
```

---

## 4. 房间接口 `/api/rooms`

### 4.1 创建房间

```
POST /api/rooms
```

**Headers:** `Authorization: Bearer <token>`

**Request Body:**

```json
{
  "wordbook_id": 1,
  "total_rounds": 5,
  "game_mode": "TURN_BASED"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `wordbook_id` | Long | 是 | 词库 ID |
| `total_rounds` | Integer | 是 | 总回合数 |
| `game_mode` | String | 是 | `TURN_BASED`（轮流）或 `RACE`（抢答） |

**Response `200`:**

```json
{
  "room": {
    "id": 1,
    "room_code": "A1B2C3",
    "host": { "id": 1, "nickname": "玩家Nick" },
    "guest": null,
    "wordbook_id": 1,
    "wordbook_name": "CET-4 核心词汇",
    "game_mode": "TURN_BASED",
    "status": "WAITING",
    "total_rounds": 5,
    "winner_id": null,
    "host_score": null,
    "guest_score": null,
    "created_at": "2026-06-04T10:00:00Z",
    "started_at": null,
    "finished_at": null
  }
}
```

---

### 4.2 加入房间

```
POST /api/rooms/join
```

**Headers:** `Authorization: Bearer <token>`

**Request Body:**

```json
{
  "room_code": "A1B2C3"
}
```

| 参数 | 类型 | 必填 | 约束 |
|------|------|------|------|
| `room_code` | String | 是 | 6 位字符 |

**Response `200`:** 同创建房间返回格式。

---

### 4.3 查询房间信息

```
GET /api/rooms/{id}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 房间 ID |

**Response `200`:** 同创建房间返回的 `room` 结构。

---

### 4.4 离开房间

```
POST /api/rooms/{id}/leave
```

**Headers:** `Authorization: Bearer <token>`

**Response `200`:** 空 body。

---

### 4.5 开始游戏

```
POST /api/rooms/{id}/start
```

**Headers:** `Authorization: Bearer <token>`

**Response `200`:**

```json
{
  "status": "started"
}
```

---

## 5. 词库接口 `/api/wordbooks`

### 5.1 获取词库列表

```
GET /api/wordbooks
```

**Response `200`:**

```json
{
  "wordbooks": [
    {
      "id": 1,
      "name": "CET-4 核心词汇",
      "description": "大学英语四级核心词汇",
      "word_count": 500
    }
  ]
}
```

---

### 5.2 获取词库详情

```
GET /api/wordbooks/{id}
```

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 词库 ID |

**Response `200`:**

```json
{
  "wordbook": {
    "id": 1,
    "name": "CET-4 核心词汇",
    "description": "大学英语四级核心词汇",
    "word_count": 500
  }
}
```

---

### 5.3 获取词库单词列表（分页）

```
GET /api/wordbooks/{id}/words?page=0&size=20
```

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `id` | Long | 是 | — | 词库 ID（路径参数） |
| `page` | Integer | 否 | 0 | 页码 |
| `size` | Integer | 否 | 20 | 每页数量 |

**Response `200`:**

```json
{
  "words": [...],
  "total": 500,
  "page": 0,
  "size": 20
}
```

---

## 6. 历史与统计接口 `/api/history` & `/api/stats`

### 6.1 获取游戏历史列表

```
GET /api/history/rooms?page=0&size=20
```

**Headers:** `Authorization: Bearer <token>`

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|------|------|------|
| `page` | Integer | 否 | 0 | 页码 |
| `size` | Integer | 否 | 20 | 每页数量 |

**Response `200`:**

```json
{
  "rooms": [
    {
      "id": 1,
      "room_id": 1,
      "room_code": "A1B2C3",
      "user_id": 1,
      "nickname": "玩家Nick",
      "score": 80,
      "correct_count": 4,
      "wrong_count": 1,
      "total_questions": 5,
      "created_at": "2026-06-04T10:00:00Z",
      "rounds": [...]
    }
  ],
  "total": 10,
  "page": 0,
  "size": 20
}
```

---

### 6.2 获取单局游戏详情

```
GET /api/history/rooms/{id}
```

**Headers:** `Authorization: Bearer <token>`

| 参数 | 类型 | 说明 |
|------|------|------|
| `id` | Long | 房间 ID |

**Response `200`:**

```json
{
  "id": 1,
  "room_id": 1,
  "room_code": "A1B2C3",
  "user_id": 1,
  "nickname": "玩家Nick",
  "score": 80,
  "correct_count": 4,
  "wrong_count": 1,
  "total_questions": 5,
  "created_at": "2026-06-04T10:00:00Z",
  "rounds": [
    {
      "round_number": 1,
      "question_type": "spell",
      "user_answer": "apple",
      "is_correct": true,
      "time_spent_ms": 3500
    }
  ]
}
```

| `rounds[].question_type` | 说明 |
|--------------------------|------|
| `spell` | 拼写题 |
| `choice` | 选择题 |

---

### 6.3 获取个人统计数据

```
GET /api/stats/me
```

**Headers:** `Authorization: Bearer <token>`

**Response `200`:**

```json
{
  "total_games": 10,
  "wins": 7,
  "losses": 3,
  "avg_score": 85.5
}
```

---

## 7. WebSocket 接口 `/ws/room`

### 7.1 连接地址

```
ws://localhost:8080/ws/room?roomId=123&token=<jwt_token>
ws://localhost:8080/ws/room?roomCode=ABC123&token=<jwt_token>
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `roomId` | Long | 二选一 | 房间 ID |
| `roomCode` | String | 二选一 | 6 位房间码 |
| `token` | String | 是 | JWT 令牌 |

### 7.2 消息格式

所有 WebSocket 消息使用统一信封格式：

```json
{
  "type": "<message_type>",
  "payload": { ... }
}
```

### 7.3 客户端 → 服务端消息

#### 开始游戏

```json
{
  "type": "game:start"
}
```

> 仅房主可发送。

#### 玩家准备

```json
{
  "type": "player:ready"
}
```

#### 输入状态

```json
{
  "type": "player:input"
}
```

> 发送后对手收到 `opponent:status {status: "typing"}`。

#### 提交答案

```json
{
  "type": "answer:submit",
  "payload": {
    "round": 1,
    "answer": "apple"
  }
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `round` | int | 是 | 回合号 |
| `answer` | string | 是 | 玩家答案 |
| `timestamp` | long | 否 | 提交时间戳 |

---

### 7.4 服务端 → 客户端消息

#### `room:joined` — 加入房间（连接成功时）

```json
{
  "type": "room:joined",
  "payload": {
    "players": [
      { "id": 1, "nickname": "玩家Nick", "is_host": true },
      { "id": 2, "nickname": "对手", "is_host": false }
    ],
    "host_id": 1,
    "word_book": { "id": 1, "name": "CET-4 核心词汇" },
    "room_code": "A1B2C3",
    "status": "WAITING"
  }
}
```

#### `opponent:status` — 对手状态变化

```json
{
  "type": "opponent:status",
  "payload": {
    "user_id": 2,
    "status": "connected" | "disconnected" | "typing" | "submitted"
  }
}
```

#### `player:ready_status` — 玩家准备状态

```json
{
  "type": "player:ready_status",
  "payload": {
    "user_id": 1,
    "ready": true
  }
}
```

#### `game:start` — 游戏开始

```json
{
  "type": "game:start",
  "payload": {
    "total_rounds": 5,
    "game_mode": "TURN_BASED"
  }
}
```

#### `question:new` — 新题目（发给当前回合玩家）

```json
{
  "type": "question:new",
  "payload": {
    "round": 1,
    "question_type": "spell" | "choice",
    "chinese": "苹果",
    "options": null,
    "time_limit": 15
  }
}
```

> `question_type` 为 `"spell"` 时 `options` 为 `null`；为 `"choice"` 时 `options` 为 `["apple", "apply", "april", "ape"]`。

#### `turn:start` — 回合开始

```json
{
  "type": "turn:start",
  "payload": {
    "current_player_id": 1
  }
}
```

#### `timer:tick` — 倒计时

```json
{
  "type": "timer:tick",
  "payload": {
    "time_left": 10
  }
}
```

> 每秒发送一次。

#### `answer:result` — 答题结果（发给答题玩家）

```json
{
  "type": "answer:result",
  "payload": {
    "correct": true,
    "player_id": 1,
    "answer": "apple"
  }
}
```

#### `score:update` — 分数更新（广播）

```json
{
  "type": "score:update",
  "payload": {
    "scores": { "1": 80, "2": 60 }
  }
}
```

#### `game:end` — 游戏结束（广播）

```json
{
  "type": "game:end",
  "payload": {
    "winner": 1,
    "scores": { "1": 80, "2": 60 },
    "stats": {
      "1": { "correct": 4, "wrong": 1, "avg_time": 3.5 },
      "2": { "correct": 3, "wrong": 2, "avg_time": 4.2 }
    }
  }
}
```

> `winner` 为 `null` 时表示平局。

#### `player:left` — 玩家离开

```json
{
  "type": "player:left",
  "payload": {
    "user_id": 2
  }
}
```

#### `room:closed` — 房间关闭

```json
{
  "type": "room:closed",
  "payload": {
    "room_id": 1
  }
}
```

#### `error` — 错误消息

```json
{
  "type": "error",
  "payload": {
    "code": "INVALID_SESSION",
    "message": "Session not found for room"
  }
}
```

| 错误码 | 说明 |
|--------|------|
| `INVALID_SESSION` | Session 无效 |
| `UNKNOWN_MESSAGE_TYPE` | 未知消息类型 |
| `PARSE_ERROR` | JSON 解析错误 |
| `ANSWER_ERROR` | 答案提交失败 |
| `GAME_START_FAILED` | 游戏启动失败 |

---

## 8. 全局错误响应

所有 REST 接口错误返回统一格式：

```json
{
  "code": "ERROR_CODE",
  "message": "错误描述"
}
```

| HTTP 状态 | code | 说明 |
|-----------|------|------|
| 400 | `VALIDATION_ERROR` | 请求参数校验失败 |
| 400-500 | 自定义 | `BusinessException` 抛出的业务异常 |
| 500 | `INTERNAL_ERROR` | 服务器内部错误 |

---

## 附录：接口汇总

| # | 方法 | 路径 | 认证 |
|---|------|------|:----:|
| 1 | POST | `/api/auth/register` | 否 |
| 2 | POST | `/api/auth/login` | 否 |
| 3 | POST | `/api/auth/refresh` | 否 |
| 4 | POST | `/api/auth/logout` | 否 |
| 5 | GET | `/api/auth/me` | 是 |
| 6 | GET | `/api/users/me` | 是 |
| 7 | GET | `/api/users/{id}` | 否 |
| 8 | POST | `/api/rooms` | 是 |
| 9 | POST | `/api/rooms/join` | 是 |
| 10 | GET | `/api/rooms/{id}` | 否 |
| 11 | POST | `/api/rooms/{id}/leave` | 是 |
| 12 | POST | `/api/rooms/{id}/start` | 是 |
| 13 | GET | `/api/wordbooks` | 否 |
| 14 | GET | `/api/wordbooks/{id}` | 否 |
| 15 | GET | `/api/wordbooks/{id}/words` | 否 |
| 16 | GET | `/api/history/rooms` | 是 |
| 17 | GET | `/api/history/rooms/{id}` | 是 |
| 18 | GET | `/api/stats/me` | 是 |

**WebSocket:** `ws://host/ws/room?roomId/roomCode&token=` — 4 种客户端消息 + 14 种服务端消息。
