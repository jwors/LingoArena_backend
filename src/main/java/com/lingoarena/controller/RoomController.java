package com.lingoarena.controller;

import com.lingoarena.dto.request.CreateRoomRequest;
import com.lingoarena.dto.request.JoinRoomRequest;
import com.lingoarena.dto.response.RoomResponse;
import com.lingoarena.service.GameService;
import com.lingoarena.service.RoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 房间控制器。
 * 创建房间、加入房间、查询房间信息。
 */
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;
    private final GameService gameService;

    /** 创建房间 */
    @PostMapping
    public ResponseEntity<Map<String, RoomResponse>> create(
            @Valid @RequestBody CreateRoomRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(Map.of("room", roomService.createRoom(request, userId)));
    }

    /** 通过 6 位房间码加入房间 */
    @PostMapping("/join")
    public ResponseEntity<Map<String, RoomResponse>> join(
            @Valid @RequestBody JoinRoomRequest request,
            @AuthenticationPrincipal Long userId) {
        return ResponseEntity.ok(Map.of("room", roomService.joinRoom(request.getRoomCode(), userId)));
    }

    /** 获取房间信息 */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, RoomResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(Map.of("room", roomService.getRoom(id)));
    }

    /** 房主开始游戏（需要双方都已准备） */
    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, String>> start(
            @PathVariable Long id,
            @AuthenticationPrincipal Long userId) {
        gameService.startGame(id, userId);
        return ResponseEntity.ok(Map.of("status", "started"));
    }
}
