# LingoArena WebSocket 协议文档

> 基于 Spring Boot `TextWebSocketHandler` 实现，路径 `/ws/room`

---

## 目录

1. [连接](#1-连接)
2. [消息格式](#2-消息格式)
3. [客户端 → 服务端消息](#3-客户端--服务端消息)
4. [服务端 → 客户端消息](#4-服务端--客户端消息)
5. [完整交互流程](#5-完整交互流程)
6. [DTO 类清单](#6-dto-类清单)

---

## 1. 连接

**端点：** `ws://localhost:8080/ws/room`

**连接参数：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `roomId` | Long | 二选一 | 房间 ID |
| `roomCode` | String | 二选一 | 6 位房间码 |
| `token` | String | 是 | JWT 令牌 |

**示例：**
```
ws://localhost:8080/ws/room?roomId=123&token=eyJhbGci...
ws://localhost:8080/ws/room?roomCode=ABC123&token=eyJhbGci...
```

**鉴权：** 握手阶段通过 `JwtHandshakeInterceptor` 校验 JWT，无效令牌返回 HTTP 401。

**连接成功：** 服务端立即推送 `room:joined` 给连接者，并广播 `opponent:status(connected)` 给房间其他人。

---

## 2. 消息格式

所有消息使用统一信封格式：

```json
{
  "type": "<message_type>",
  "payload": { ... }
}
```

- `type` — 字符串，标识消息类型
- `payload` — JSON 对象，消息体内容

---

## 3. 客户端 → 服务端消息

客户端可发送以下 4 种消息类型，由 `RoomWebSocketHandler.handleTextMessage()` 分发处理。

---

### 3.1 game:start — 开始游戏

仅房主可发送。触发游戏初始化、题目加载、广播 `game:start`。

```json
{
  "type": "game:start"
}
```

> 无 payload。

**校验条件：**
- 发送者必须是房主
- 房间状态必须为 `WAITING`
- 房间必须有两位玩家（host + guest）
- 双方必须都已准备（`areBothReady()`）
- 必须已选词库

**失败时推送 `error(GAME_START_FAILED)`。**

---

### 3.2 player:ready — 玩家准备

```json
{
  "type": "player:ready"
}
```

> 无 payload。

**效果：**
- 调用 `GameService.setPlayerReady()` 标记玩家已准备
- 广播 `player:ready_status {userId, ready: true}` 给房间

---

### 3.3 player:input — 输入中状态

```json
{
  "type": "player:input"
}
```

> 无 payload。

**效果：**
- 广播 `opponent:status {userId, status: "typing"}` 给房间（不含发送者）

---

### 3.4 answer:submit — 提交答案

```json
{
  "type": "answer:submit",
  "payload": {
    "round": 1,
    "answer": "apple",
    "timestamp": 1717488000000
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `round` | int | 是 | 当前回合号 |
| `answer` | string | 是 | 玩家提交的答案 |
| `timestamp` | long | 否 | 提交时间戳（ms），缺省为系统当前时间 |

**处理流程：**
1. 获取当前轮次 → 核验答案 → `GameService.submitAnswer()`
2. 推送 `answer:result`（仅答题者）
3. 广播 `score:update`
4. 广播 `opponent:status(submitted)`
5. 取消倒计时
6. 检查游戏是否结束 → 结束则推送 `game:end`
7. 否则推下一题给对手

**失败时推送 `error(ANSWER_ERROR)`。**

---

## 4. 服务端 → 客户端消息

服务端可发送以下 13 种消息类型。

---

### 4.1 room:joined — 加入房间

**推送时机：** WS 连接成功时（个人） / 第二位玩家加入时（广播）

**DTO:** `RoomJoinedMessage`

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

| 字段 | 类型 | 说明 |
|------|------|------|
| `players` | array | 房间内玩家列表 |
| `players[].id` | Long | 玩家 ID |
| `players[].nickname` | String | 玩家昵称 |
| `players[].is_host` | Boolean | 是否为房主 |
| `host_id` | Long | 房主 ID |
| `word_book` | Object | 词库信息（可选，可能为 null） |
| `word_book.id` | Long | 词库 ID |
| `word_book.name` | String | 词库名称 |
| `room_code` | String | 6 位房间码 |
| `status` | String | 当前房间状态 |

---

### 4.2 opponent:status — 对手状态变化

**推送时机：** 连接 / 断开 / 输入中 / 已提交

**DTO:** `OpponentStatusMessage`

```json
{
  "type": "opponent:status",
  "payload": {
    "user_id": 2,
    "status": "connected"
  }
}
```

| status 值 | 触发条件 |
|-----------|----------|
| `connected` | 玩家 WS 连接建立 |
| `disconnected` | 玩家 WS 连接关闭 |
| `typing` | 玩家发送 `player:input` |
| `submitted` | 玩家提交答案后 |

---

### 4.3 player:ready_status — 玩家准备状态

**推送时机：** 玩家发送 `player:ready` 后

**DTO:** `PlayerReadyMessage`

```json
{
  "type": "player:ready_status",
  "payload": {
    "user_id": 1,
    "ready": true
  }
}
```

---

### 4.4 game:start — 游戏开始

**推送时机：** 房主触发开始游戏，校验通过后

**DTO:** `GameStartMessage`

```json
{
  "type": "game:start",
  "payload": {
    "total_rounds": 5,
    "game_mode": "turn_based"
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `total_rounds` | int | 总回合数 |
| `game_mode` | String | `"turn_based"`（轮流）或 `"rush"`（抢答） |

**game:start 之后紧接着推送第一道 `turn:start` + `question:new` 给房主。**

---

### 4.5 turn:start — 回合开始

**推送时机：** 每回合开始时

**DTO:** `TurnStartMessage`

```json
{
  "type": "turn:start",
  "payload": {
    "current_player_id": 1
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `current_player_id` | Long | 当前回合玩家 ID |

**与 `question:new` 同时推送，但 `turn:start` 是广播，`question:new` 仅发个人。**

---

### 4.6 question:new — 新题目

**推送时机：** 轮到某位玩家答题时

**DTO:** `NewQuestionMessage`

**推送范围：** 仅当前回合玩家（`sendToUser`）

```json
{
  "type": "question:new",
  "payload": {
    "round": 1,
    "question_type": "spell",
    "chinese": "苹果",
    "options": null,
    "time_limit": 15
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `round` | int | 回合号 |
| `question_type` | String | `"spell"`（拼写题）或 `"choice"`（选择题） |
| `chinese` | String | 中文释义 |
| `options` | array|null | 选择题选项，拼写题为 null |
| `time_limit` | int | 答题时限（秒） |

**`question:new` 发送后立即启动倒计时，每秒推送 `timer:tick`。**

---

### 4.7 timer:tick — 倒计时

**推送时机：** 每秒一次

**DTO:** `TimerTickMessage`

```json
{
  "type": "timer:tick",
  "payload": {
    "time_left": 10
  }
}
```

> 倒计时结束后玩家未提交 → 超时处理：
> 1. 推送 `answer:result(correct: false, answer: "timeout")` 给超时玩家
> 2. 推送 `score:update`
> 3. 检查游戏是否结束
> 4. 未结束则推下一题给对手

---

### 4.8 answer:result — 答题结果

**推送时机：** 答案核验后 / 超时后

**DTO:** `RoundResultMessage`

**推送范围：** 仅答题玩家（`sendToUser`）

**提交答案后：**

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

**超时后：**

```json
{
  "type": "answer:result",
  "payload": {
    "correct": false,
    "player_id": 1,
    "answer": "timeout"
  }
}
```

---

### 4.9 score:update — 分数更新

**推送时机：** 每次答题后 / 超时后

**DTO:** `ScoreUpdateMessage`

```json
{
  "type": "score:update",
  "payload": {
    "scores": { "1": 80, "2": 60 }
  }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `scores` | Map | `userId → score` 的映射 |

---

### 4.10 game:end — 游戏结束

**推送时机：** 所有回合完成后

**DTO:** `GameOverMessage`

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

| 字段 | 类型 | 说明 |
|------|------|------|
| `winner` | Long|null | 胜者 userId，null 表示平局 |
| `scores` | Map | `userId → score` |
| `stats` | Map | `userId → PlayerStats` |
| `stats[].correct` | int | 正确数 |
| `stats[].wrong` | int | 错误数 |
| `stats[].avg_time` | double | 平均答题用时（秒） |

**game:end 推送后清理游戏状态（`GameManager.cleanupGame()`）。**

---

### 4.11 player:left — 玩家离开

**推送时机：** 玩家主动调用退出接口后

```json
{
  "type": "player:left",
  "payload": {
    "user_id": 2
  }
}
```

> 紧接着推送 `room:closed` 并断开所有连接。

---

### 4.12 room:closed — 房间关闭

**推送时机：** 玩家离开后房间销毁

**DTO:** `RoomClosedMessage`

```json
{
  "type": "room:closed",
  "payload": {
    "room_id": 1
  }
}
```

> 收到此消息后客户端应关闭 WebSocket 连接。

---

### 4.13 error — 错误消息

**推送时机：** 各种异常情况

**DTO:** `ErrorMessage`

```json
{
  "type": "error",
  "payload": {
    "code": "INVALID_SESSION",
    "message": "连接无效，缺少用户信息"
  }
}
```

| 错误码 | 触发条件 | 源代码位置 |
|--------|----------|-----------|
| `INVALID_SESSION` | session 缺少 roomId 或 userId | `RoomWebSocketHandler:85` |
| `UNKNOWN_TYPE` | 收到未知的 type 值 | `RoomWebSocketHandler:94` |
| `MESSAGE_PARSE_ERROR` | JSON 解析失败 | `RoomWebSocketHandler:98` |
| `GAME_START_FAILED` | 游戏启动校验失败 | `RoomWebSocketHandler:135` |
| `ANSWER_ERROR` | 答案提交失败 | `RoomWebSocketHandler:170` |

---

## 5. 完整交互流程

### 5.1 正常游戏流程

```
客户端A (房主)          服务端              客户端B (对手)
    |                     |                    |
    |--- WS connect ----->|                    |
    |<-- room:joined -----|                    |
    |                     |--- WS connect ---->|
    |                     |<-- player:ready ---|
    |<-- room:joined -----|                    |
    |                     |<-- player:ready ---|
    |<-- player:ready_s --|                    |
    |                     |-- player:ready_s ->|
    |--- game:start ----->|                    |
    |<-- game:start ------|-- game:start ----->|
    |<-- turn:start ------|-- turn:start ----->|  (房主先手)
    |<-- question:new ----|                    |
    |<-- timer:tick ------|-- timer:tick ----->|  (每秒)
    |--- answer:submit -->|                    |
    |<-- answer:result ---|                    |
    |<-- score:update ----|-- score:update --->|
    |                     |-- turn:start ----->|  (轮到对手)
    |                     |-- question:new --->|
    |                     |-- timer:tick ----->|  (每秒)
    |                     |<-- answer:submit ---|
    |<-- score:update ----|-- answer:result ---|
    |                     |-- score:update --->|
    |        ... (重复到最后一轮) ...          |
    |<-- game:end --------|-- game:end ------->|
```

### 5.2 超时流程

```
    服务端                    客户端
       |                        |
       |--- timer:tick (5) ---->|
       |--- timer:tick (4) ---->|
       |--- timer:tick (3) ---->|
       |--- timer:tick (2) ---->|
       |--- timer:tick (1) ---->|
       |--- timer:tick (0) ---->|
       |                        |  (超时，玩家未提交)
       |--- answer:result ----->|  {correct: false, answer: "timeout"}
       |--- score:update ------>|
       |--- 推下一题给对手       |
```

### 5.3 退出流程

```
    客户端                    服务端
       |                        |
       |--- POST /leave ------->|  REST 接口触发
       |<-- 200 OK -------------|
       |                        |--- player:left ---> 对手
       |                        |--- room:closed ---> 对手
       |                        |--- 断开所有 WS 连接
```

---

## 6. DTO 类清单

所有 DTO 位于 `com.lingoarena.dto.websocket` 包：

| 类名 | 方向 | 用途 |
|------|------|------|
| `WebSocketMessage<T>` | — | 统一消息信封 `{"type", "payload"}` |
| `SubmitAnswerMessage` | C2S | 答案提交请求体 |
| `RoomJoinedMessage` | S2C | 房间状态快照 |
| `OpponentStatusMessage` | S2C | 对手状态通知 |
| `PlayerReadyMessage` | S2C | 玩家准备状态 |
| `GameStartMessage` | S2C | 游戏开始通知 |
| `NewQuestionMessage` | S2C | 题目推送 |
| `TurnStartMessage` | S2C | 回合开始 |
| `TimerTickMessage` | S2C | 倒计时 |
| `RoundResultMessage` | S2C | 答题结果 |
| `ScoreUpdateMessage` | S2C | 分数更新 |
| `GameOverMessage` | S2C | 游戏结束 |
| `PlayerLeftMessage`（`Map`） | S2C | 玩家离开 |
| `RoomClosedMessage` | S2C | 房间关闭 |
| `ErrorMessage` | S2C | 错误消息 |

> 注：`player:left` 未使用独立 DTO，直接使用 `Map.of("userId", userId)` 作为 payload。
