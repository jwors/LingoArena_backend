package com.lingoarena.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingoarena.TestLingoArenaApplication;
import com.lingoarena.dto.request.RegisterRequest;
import com.lingoarena.dto.response.AuthResponse;
import com.lingoarena.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.*;
import org.springframework.context.annotation.Import;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证房间创建后 WebSocket 推送内容的集成测试。
 *
 * 测试场景：
 * 1. 玩家注册 → 获取 JWT token
 * 2. 通过 REST API 创建房间
 * 3. 连接 WebSocket
 * 4. 验证收到 room:joined（完整房间状态）
 * 5. 验证收到 opponent:status（连接通知）
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"
        }
)
@Import(TestLingoArenaApplication.class)
class RoomWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AuthService authService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 模拟 Redis 连接工厂，避免集成测试需要真实 Redis 实例。
     * GameManager 中的 Redis 操作（题目队列、答案暂存）在游戏阶段才会用到，
     * 本测试只验证房间创建 + WS 连接阶段，不会触发 Redis 调用。
     */
    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    private String hostToken;
    private Long hostId;
    private Long roomId;
    private String roomCode;

    @BeforeEach
    void setUp() {
        // ---- 注册房主 ----
        RegisterRequest req = new RegisterRequest();
        // 用时间戳保证每次测试邮箱唯一（Testcontainers 跨测试复用容器）
        req.setEmail("host_" + System.nanoTime() + "@test.com");
        req.setPassword("password123");
        req.setNickname("HostPlayer");
        AuthResponse auth = authService.register(req);
        hostToken = auth.getAccessToken();
        hostId = auth.getUser().getId();

        // ---- 通过 REST API 创建房间 ----
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(hostToken);

        ResponseEntity<String> resp = restTemplate.exchange(
                "/api/rooms",
                HttpMethod.POST,
                new HttpEntity<>("{}", headers),
                String.class);

        assertNotNull(resp.getBody());
        JsonNode json = objectMapper.readTree(resp.getBody());
        roomId = json.get("room").get("id").asLong();
        roomCode = json.get("room").get("room_code").asText();

        assertNotNull(roomId);
        assertNotNull(roomCode);
    }

    @Test
    void testRoomJoinedAndOpponentStatusAfterWsConnection() throws Exception {
        // ---- 收集 WS 消息的队列 ----
        BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();

        // ---- 连接 WebSocket ----
        HttpClient httpClient = HttpClient.newHttpClient();
        httpClient.newWebSocketBuilder()
                .buildAsync(
                        URI.create(String.format(
                                "ws://localhost:%d/ws/room?token=%s&roomId=%d",
                                port, hostToken, roomId)),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket ws,
                                                             CharSequence data,
                                                             boolean last) {
                                messageQueue.offer(data.toString());
                                return WebSocket.Listener.super.onText(ws, data, last);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        // ---- 等待并收集消息（最多等 3 秒，收满 5 条为止）----
        List<String> receivedMessages = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline && receivedMessages.size() < 5) {
            String msg = messageQueue.poll(300, TimeUnit.MILLISECONDS);
            if (msg != null) {
                receivedMessages.add(msg);
            }
        }

        assertFalse(receivedMessages.isEmpty(),
                "WS 连接后应至少收到一条消息");

        // ---- 从收到的消息中查找 room:joined ----
        JsonNode roomJoinedMsg = null;
        for (String msg : receivedMessages) {
            JsonNode node = objectMapper.readTree(msg);
            if ("room:joined".equals(node.get("type").asText())) {
                roomJoinedMsg = node;
                break;
            }
        }

        assertNotNull(roomJoinedMsg,
                "WS 连接后必须收到 room:joined 消息，实际收到的消息: " + receivedMessages);

        // ---- 验证 room:joined payload ----
        JsonNode payload = roomJoinedMsg.get("payload");

        // 玩家列表：刚创建房间只有房主 1 人
        assertTrue(payload.has("players"), "room:joined 应包含 players 字段");
        assertEquals(1, payload.get("players").size(),
                "刚创建的房间 players 列表应有 1 人");
        assertEquals(hostId.longValue(), payload.get("players").get(0).get("id").asLong(),
                "players[0].id 应为房主 ID");
        assertEquals("HostPlayer", payload.get("players").get(0).get("nickname").asText(),
                "players[0].nickname 应匹配");
        assertTrue(payload.get("players").get(0).get("is_host").asBoolean(),
                "players[0].is_host 应为 true");

        // 房主 ID
        assertEquals(hostId.longValue(), payload.get("host_id").asLong(),
                "host_id 应匹配");

        // 房间状态
        assertEquals("WAITING", payload.get("status").asText(),
                "新创建房间状态应为 WAITING");

        // 房间码
        assertEquals(roomCode, payload.get("room_code").asText(),
                "room_code 应匹配");

        // ---- 验证 opponent:status ----
        boolean hasOpponentStatus = receivedMessages.stream().anyMatch(msg -> {
            try {
                return "opponent:status".equals(
                        objectMapper.readTree(msg).get("type").asText());
            } catch (Exception e) {
                return false;
            }
        });
        assertTrue(hasOpponentStatus,
                "WS 连接后还应收到 opponent:status 消息");
    }

    @Test
    void testRoomJoinedAfterGuestJoins() throws Exception {
        // ---- 房主先连接 WS ----
        BlockingQueue<String> hostQueue = new LinkedBlockingQueue<>();
        HttpClient httpClient = HttpClient.newHttpClient();
        httpClient.newWebSocketBuilder()
                .buildAsync(
                        URI.create(String.format(
                                "ws://localhost:%d/ws/room?token=%s&roomId=%d",
                                port, hostToken, roomId)),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket ws,
                                                             CharSequence data,
                                                             boolean last) {
                                hostQueue.offer("[HOST] " + data);
                                return WebSocket.Listener.super.onText(ws, data, last);
                            }
                        })
                .get(5, TimeUnit.SECONDS);

        // 等房主 WS 连接稳定
        Thread.sleep(500);
        hostQueue.drainTo(new ArrayList<>()); // 清空初始化消息

        // ---- 游客注册 + 加入房间 ----
        RegisterRequest guestReq = new RegisterRequest();
        guestReq.setEmail("guest_" + System.nanoTime() + "@test.com");
        guestReq.setPassword("password123");
        guestReq.setNickname("GuestPlayer");
        AuthResponse guestAuth = authService.register(guestReq);

        HttpHeaders guestHeaders = new HttpHeaders();
        guestHeaders.setContentType(MediaType.APPLICATION_JSON);
        guestHeaders.setBearerAuth(guestAuth.getAccessToken());

        // 游客先连接 WS（通过 roomCode）
        BlockingQueue<String> guestQueue = new LinkedBlockingQueue<>();
        httpClient.newWebSocketBuilder()
                .buildAsync(
                        URI.create(String.format(
                                "ws://localhost:%d/ws/room?token=%s&roomCode=%s",
                                port, guestAuth.getAccessToken(), roomCode)),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket ws,
                                                             CharSequence data,
                                                             boolean last) {
                                guestQueue.offer("[GUEST] " + data);
                                return WebSocket.Listener.super.onText(ws, data, last);
                            }
                        })
                .get(5, TimeUnit.SECONDS);
        Thread.sleep(500);
        guestQueue.drainTo(new ArrayList<>()); // 清空初始化消息

        // ---- 游客通过 REST API 加入房间 ----
        HttpEntity<String> joinEntity = new HttpEntity<>(
                "{\"room_code\":\"" + roomCode + "\"}",
                guestHeaders);
        ResponseEntity<String> joinResp = restTemplate.exchange(
                "/api/rooms/join",
                HttpMethod.POST,
                joinEntity,
                String.class);
        assertEquals(200, joinResp.getStatusCode().value());

        // ---- 等待消息 ----
        Thread.sleep(1000);

        // ---- 房主应收到新的 room:joined（含 2 名玩家）----
        List<String> hostMsgs = new ArrayList<>();
        hostQueue.drainTo(hostMsgs);

        JsonNode hostRoomJoined = null;
        for (String msg : hostMsgs) {
            JsonNode node = objectMapper.readTree(msg.replace("[HOST] ", ""));
            if ("room:joined".equals(node.get("type").asText())
                    && node.get("payload").has("players")
                    && node.get("payload").get("players").size() == 2) {
                hostRoomJoined = node;
                break;
            }
        }
        assertNotNull(hostRoomJoined,
                "房主应收到含 2 名玩家的 room:joined，实际消息: " + hostMsgs);

        JsonNode hostPayload = hostRoomJoined.get("payload");
        assertEquals(2, hostPayload.get("players").size(),
                "游客加入后 players 应有 2 人");
        String nickname1 = hostPayload.get("players").get(0).get("nickname").asText();
        String nickname2 = hostPayload.get("players").get(1).get("nickname").asText();
        assertTrue(
                (nickname1.equals("HostPlayer") && nickname2.equals("GuestPlayer"))
                        || (nickname1.equals("GuestPlayer") && nickname2.equals("HostPlayer")),
                "players 应包含 HostPlayer 和 GuestPlayer，实际: " + nickname1 + ", " + nickname2);

        // ---- 游客应收到 room:joined（自身信息）----
        List<String> guestMsgs = new ArrayList<>();
        guestQueue.drainTo(guestMsgs);

        boolean guestGotRoomJoined = guestMsgs.stream().anyMatch(msg -> {
            try {
                return "room:joined".equals(
                        objectMapper.readTree(msg.replace("[GUEST] ", "")).get("type").asText());
            } catch (Exception e) {
                return false;
            }
        });
        assertTrue(guestGotRoomJoined, "游客也应收到 room:joined");
    }
}
