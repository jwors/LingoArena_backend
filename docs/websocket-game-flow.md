# WebSocket 游戏消息协议

## 架构

```
REST API（房间管理）                 WebSocket（游戏阶段）
────────────────────────────────────────────────────────
POST /api/rooms                     room:joined（连接后推送房间状态）
POST /api/rooms/join                opponent:status（连接/断开/typing/submitted）
POST /api/rooms/{id}/leave          player:ready_status（准备状态变化）
POST /api/rooms/{id}/start          player:left（玩家退出）
GET  /api/rooms/{id}                room:closed（房间销毁）
                                    player_ready（上行）
                                    player:input（上行，输入状态提示）
                                    submit_answer（上行）
                                    game:start（上行，兼容 WS 触发）
                                    game:start（下行）
                                    turn:start（通知轮到谁）
                                    question:new（出题）
                                    timer:tick（倒计时每秒推送）
                                    answer:result（答题结果）
                                    score:update（分数更新）
                                    game:end（游戏结束含战绩）
                                    error
```

## WS 消息列表

### 服务端 → 客户端

| type | 触发时机 | 发给谁 | payload |
|---|---|---|---|
| `room:joined` | WS 连接成功 | 仅连接者 | `{players, hostId, wordBook, roomCode, status}` |
| `opponent:status` | 对方连上/断开/输入中/已提交 | 全房间广播 | `{userId, status: "connected"\|"disconnected"\|"typing"\|"submitted"}` |
| `player:ready_status` | 有玩家准备 | 全房间广播 | `{userId, ready: true}` |
| `player:left` | 有玩家主动退出 | 全房间广播 | `{userId}` |
| `room:closed` | 房间被销毁 | 全房间广播 | `{roomId}` |
| `game:start` | 游戏开始 | 全房间广播 | `{totalRounds, gameMode: "turn_based"\|"rush"}` |
| `turn:start` | 轮到某玩家 | 全房间广播 | `{currentPlayerId}` |
| `question:new` | 出题 | 仅答题者 | `{round, questionType: "spell"\|"choice", chinese, options, timeLimit}` |
| `timer:tick` | 每秒倒计时 | 全房间广播 | `{timeLeft}` |
| `answer:result` | 收到答案后 | 仅答题者 | `{correct, playerId, answer}` |
| `score:update` | 每次答题后 | 全房间广播 | `{scores: {id: score}}` |
| `game:end` | 游戏结束 | 全房间广播 | `{winner, scores: {id: score}, stats: {id: {correct, wrong, avgTime}}}` |
| `error` | 出错 | 仅发送者 | `{code, message}` |

### 客户端 → 服务端

| type | payload | 说明 |
|---|---|---|
| `player:ready` | 无 | 标记已准备 |
| `game:start` | 无 | 触发游戏开始（WS 兼容方式） |
| `player:input` | `{round}` | 通知服务端正在输入，透传给对手 |
| `answer:submit` | `{answer, timestamp?}` | 提交答案（round 由服务端维护） |

## 完整游戏流程

```
1. A 创建房间 (POST /api/rooms) → 返回 roomId, roomCode
2. B 加入房间 (POST /api/rooms/join, roomCode)
   → 广播 room:joined {players: [A, B], hostId, roomCode, status}
3. A 连接 WS → 收到 room:joined（完整房间状态）
           → B 收到 opponent:status {connected}
4. B 连接 WS → 收到 room:joined（完整房间状态）
           → A 收到 opponent:status {connected}
5. A 发 player:ready → 广播 player:ready_status {userId: A, ready: true}
6. B 发 player:ready → 广播 player:ready_status {userId: B, ready: true}
7. A 发 game:start（WS 或 REST）
   → 广播 game:start {totalRounds, gameMode}
   → 广播 turn:start {currentPlayerId: A}
   → A 收到 question:new {round, questionType, chinese, options, timeLimit}
   → 每秒广播 timer:tick {timeLeft}
8. A 发 answer:submit {answer}
   → A 收到 answer:result {correct, playerId: A, answer}
   → 广播 score:update {scores: {A: 10, B: 0}}
   → 广播 opponent:status {userId: A, status: "submitted"}
   → 取消 timer:tick
   → 广播 turn:start {currentPlayerId: B}
   → B 收到 question:new（下一题）
   → 新的 timer:tick 开始
9. B 发 answer:submit → 同上
10. 循环直到题目队列为空
    → 广播 game:end {winner, scores, stats}
```

## 退出房间

任何时候玩家可点击退出按钮，调用 `POST /api/rooms/{id}/leave`：

```
1. 客户端调 POST /api/rooms/{id}/leave
2. 服务端：
   a. 校验用户属于该房间
   b. 如正在游戏，清理游戏状态
   c. 广播 player:left {userId} + room:closed {roomId}
   d. 房间状态 → CANCELLED
   e. 断开该房间所有 WS 连接
3. 对方收到 player:closed/room:closed → 跳转离开
```

## 消息格式示例

### room:joined
```json
{
  "type": "room:joined",
  "payload": {
    "players": [
      { "id": 1, "nickname": "Alice", "isHost": true },
      { "id": 2, "nickname": "Bob", "isHost": false }
    ],
    "hostId": 1,
    "wordBook": { "id": 1, "name": "四级词汇" },
    "roomCode": "ABC123",
    "status": "WAITING"
  }
}
```

### game:end
```json
{
  "type": "game:end",
  "payload": {
    "winner": 1,
    "scores": { "1": 50, "2": 30 },
    "stats": {
      "1": { "correct": 5, "wrong": 0, "avgTime": 2.1 },
      "2": { "correct": 3, "wrong": 2, "avgTime": 3.5 }
    }
  }
}
```

## 变更记录

### 2026-06-01

- **重构** 所有 WS 消息改用 DTO + ObjectMapper 序列化，替代手拼 JSON
- **新增** `room:joined` — WS 连接后推送完整房间状态（players, hostId, wordBook, roomCode）
- **新增** `player:ready_status` — 准备状态独立消息，不再混用 opponent:status
- **新增** `turn:start` — 通知轮到哪位玩家
- **新增** `timer:tick` — 答题倒计时每秒推送
- **新增** `room:closed` — 房间销毁时通知
- **新增** `player:input` 上行消息 — 输入状态提示，透传给对手
- **新增** WS `game:start` 兼容 — 前端可通过 WS 触发游戏开始
- **新增** 战绩统计 — GameManager 追踪正误次数和平均响应时间
- **修改** `question:new` payload 字段：`content` → `chinese`
- **修改** `score:update` payload 格式：`{hostScore, guestScore}` → `{scores: {id: score}}`
- **修改** `answer:result` payload：增加 `playerId`，`correctAnswer` → `answer`
- **修改** `game:end` payload 格式：`{winnerId, hostScore, guestScore}` → `{winner, scores, stats}`
- **修改** `opponent:status` 职责：仅用于 connected/disconnected/typing/submitted
- **修改** `opponent:status` 连接消息不再携带 nickname（已包含在 room:joined）
- **分割** `player_ready` 响应：从 opponent:status → player:ready_status

### 2026-05-31

- **新增** `POST /api/rooms/{id}/leave` REST 端点，玩家主动退出房间
- **新增** WS 消息类型 `player:left`，通知对手有玩家退出
- **新增** 退出逻辑：房间直接销毁（CANCELLED），断开所有 WS 连接
- **修复** 游戏中退出时清理 GameManager 状态
- **新增** `POST /api/rooms/{id}/start` REST 端点，房主触发游戏开始
- **新增** 玩家准备机制
- **新增** 内存游戏状态管理
- **优化** 职责分离：房间管理走 REST，游戏阶段走 WS
- **优化** WebSocket 握手支持 `roomCode` 参数
- **修复** WebSocket 断开不自动取消房间
- **修复** 连接/断开时的 NPE
