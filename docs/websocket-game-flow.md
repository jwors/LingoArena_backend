# WebSocket 游戏消息协议

## 架构

```
REST API（房间管理）                 WebSocket（游戏阶段）
────────────────────────────────────────────────────────
POST /api/rooms                     opponent:status（连接/断开/准备）
POST /api/rooms/join                player_ready（上行）
POST /api/rooms/{id}/start          submit_answer（上行）
GET  /api/rooms/{id}                answer:result
                                    score:update
                                    question:new
                                    game:start / game:end
                                    error
```

## WS 消息列表

### 服务端 → 客户端

| type | 触发时机 | 发给谁 | payload |
|---|---|---|---|
| `opponent:status` | 玩家连上/断开/准备 | 全房间广播 | `{userId, status:"connected"|"disconnected"|"ready", nickname?}` |
| `game:start` | 房主调 start 接口 | 全房间广播 | `{totalRounds, gameMode}` |
| `question:new` | 轮到你答题 | 仅答题者 | `{round, questionType, content, options}` |
| `answer:result` | 提交答案后 | 仅答题者 | `{round, correct, correctAnswer, score}` |
| `score:update` | 每次答题后 | 全房间广播 | `{hostScore, guestScore}` |
| `game:end` | 所有题目答完 | 全房间广播 | `{winnerId, hostScore, guestScore}` |
| `error` | 出错 | 仅发送者 | `{code, message}` |

### 客户端 → 服务端

| type | payload | 说明 |
|---|---|---|
| `player_ready` | 无 | 标记已准备 |
| `submit_answer` | `{round, answer, timestamp?}` | 提交答案 |

## 游戏流程

```
1. A 创建房间 (POST /api/rooms)
2. B 加入房间 (POST /api/rooms/join, roomCode)
3. 双方连接 WS (ws://host/ws/room?roomCode=xxx&token=yyy)
   → 各自收到 opponent:status {status:"connected"}
4. 双方发送 player_ready
   → 各自收到 opponent:status {status:"ready"}
5. A 调 POST /api/rooms/{id}/start
   → 全房间 broadcast game:start
   → A 收到 question:new（第一题）
6. A 发送 submit_answer
   → A 收到 answer:result
   → 全房间收到 score:update
   → B 收到 question:new（下一题）
7. B 发送 submit_answer
   → B 收到 answer:result
   → 全房间收到 score:update
   → A 收到 question:new（下一轮）
8. 循环直到没有剩余题目
   → 全房间收到 game:end {winnerId, hostScore, guestScore}
```

## 变更记录

### 2026-05-31

- **新增** `POST /api/rooms/{id}/start` REST 端点，房主触发游戏开始
- **新增** 玩家准备机制：WS `player_ready` → 服务端追踪双方已准备 → `startGame` 校验
- **新增** 内存游戏状态管理（题目队列、分数、轮次），减少 Redis 依赖
- **新增** WS 消息类型对齐前端规范：`opponent:status`、`game:start`、`question:new`、`answer:result`、`score:update`、`game:end`
- **优化** 职责分离：房间管理走 REST，游戏阶段走 WS
- **优化** WebSocket 握手支持 `roomCode` 参数（除 `roomId` 外）
- **修复** WebSocket 断开不自动取消房间（由定时清理任务兜底）
- **修复** `afterConnectionEstablished`/`afterConnectionClosed` 中 roomId 为 null 时的 NPE
- **修复** `joinRoom` 区分房间已取消和游戏已开始的错误提示
- **移除** WS 中 `select_wordbook`、`room_joined` 等房间阶段消息
