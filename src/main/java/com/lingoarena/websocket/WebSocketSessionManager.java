package com.lingoarena.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 连接管理器。
 *
 * 管理所有 WebSocket 连接，支持：
 * 1. 按房间查找所有连接（广播消息用）
 * 2. 按用户查找单个连接（私信用）
 *
 * 线程安全：
 * - ConcurrentHashMap：多个线程同时操作不会死锁
 * - CopyOnWriteArraySet：遍历时修改不会抛 ConcurrentModificationException
 * - 游戏房间中两人可能同时发消息，这两个集合必须线程安全
 */
@Slf4j
@Component
public class WebSocketSessionManager {

    /** roomId -> 该房间内所有玩家的 WebSocket 连接 */
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    /** userId -> 该用户的 WebSocket 连接（方便一对一推送） */
    private final ConcurrentHashMap<Long, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    /** 添加新连接 */
    public void addSession(Long roomId, Long userId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> new CopyOnWriteArraySet<>()).add(session);
        userSessions.put(userId, session);
    }

    /** 移除连接（断开时清理） */
    public void removeSession(Long roomId, Long userId, WebSocketSession session) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(session);
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        userSessions.remove(userId, session);
    }

    /** 获取某个房间的所有连接 */
    public Set<WebSocketSession> getRoomSessions(Long roomId) {
        return roomSessions.getOrDefault(roomId, new CopyOnWriteArraySet<>());
    }

    /** 获取某个用户的连接 */
    public WebSocketSession getUserSession(Long userId) {
        return userSessions.get(userId);
    }

    /** 向房间内所有用户广播消息 */
    public void broadcastToRoom(Long roomId, String message) {
        Set<WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null) return;

        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new org.springframework.web.socket.TextMessage(message));
                } catch (IOException e) {
                    log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }

    /** 向指定用户发送消息 */
    public void sendToUser(Long userId, String message) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new org.springframework.web.socket.TextMessage(message));
            } catch (IOException e) {
                log.error("Failed to send message to user {}: {}", userId, e.getMessage());
            }
        }
    }

    /** 从 session 中获取用户 ID（在握手时存入 attributes 的） */
    public Long getUserIdFromSession(WebSocketSession session) {
        return (Long) session.getAttributes().get("userId");
    }

    /** 从 session 中获取房间 ID */
    public Long getRoomIdFromSession(WebSocketSession session) {
        return (Long) session.getAttributes().get("roomId");
    }

    /**
     * 关闭房间所有 WebSocket 连接并清理缓存。
     * 用于玩家退出房间时，断开房间内所有人的连接。
     */
    public void closeRoomConnections(Long roomId) {
        Set<WebSocketSession> sessions = roomSessions.remove(roomId);
        if (sessions == null) return;

        for (WebSocketSession session : sessions) {
            Long userId = getUserIdFromSession(session);
            if (userId != null) {
                userSessions.remove(userId, session);
            }
            if (session.isOpen()) {
                try {
                    session.close(CloseStatus.NORMAL);
                } catch (IOException e) {
                    log.error("Failed to close session {}: {}", session.getId(), e.getMessage());
                }
            }
        }
    }
