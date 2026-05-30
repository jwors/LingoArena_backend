package com.lingoarena.service;

import com.lingoarena.dto.request.CreateRoomRequest;
import com.lingoarena.dto.response.RoomResponse;
import com.lingoarena.entity.GameRoom;
import com.lingoarena.entity.User;
import com.lingoarena.entity.Wordbook;
import com.lingoarena.enums.GameMode;
import com.lingoarena.enums.RoomStatus;
import com.lingoarena.exception.BusinessException;
import com.lingoarena.exception.ErrorCode;
import com.lingoarena.mapper.GameRoomMapper;
import com.lingoarena.repository.GameRoomRepository;
import com.lingoarena.repository.UserRepository;
import com.lingoarena.repository.WordbookRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 房间服务。
 *
 * 负责创建房间、加入房间、查询房间信息。
 *
 * 房间码生成规则：
 * - 6 位字符，由大写字母（不含 I,O）和数字（不含 0,1）组成
 * - 避免混淆：I/1、O/0 已移除
 * - 生成时检查唯一性，直到生成一个未使用过的码
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private static final int ROOM_CODE_LENGTH = 6;
    /** 房间码字符集（去掉了容易混淆的 I/O/0/1） */
    private static final String ROOM_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final GameRoomRepository gameRoomRepository;
    private final UserRepository userRepository;
    private final WordbookRepository wordbookRepository;
    private final GameRoomMapper gameRoomMapper;
    private final SecureRandom random = new SecureRandom();

    /** 创建房间，返回房间信息（含 6 位房间码） */
    @Transactional
    public RoomResponse createRoom(CreateRoomRequest request, Long hostId) {
        User host = userRepository.findById(hostId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));

        Wordbook wordbook = null;
        if (request.getWordbookId() != null) {
            wordbook = wordbookRepository.findById(request.getWordbookId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.WORDBOOK_NOT_FOUND.getCode(),
                            ErrorCode.WORDBOOK_NOT_FOUND.getMessage()));
        }

        GameRoom room = GameRoom.builder()
                .roomCode(generateRoomCode())
                .host(host)
                .wordbook(wordbook)
                .gameMode(request.getGameMode() != null ? request.getGameMode() : GameMode.TURN_BASED)
                .totalRounds(request.getTotalRounds() != null ? request.getTotalRounds() : 10)
                .status(RoomStatus.WAITING)
                .build();

        room = gameRoomRepository.save(room);
        return gameRoomMapper.toResponse(room);
    }

    /** 通过房间码加入房间 */
    @Transactional
    public RoomResponse joinRoom(String roomCode, Long guestId) {
        GameRoom room = gameRoomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ROOM_CODE.getCode(),
                        ErrorCode.INVALID_ROOM_CODE.getMessage()));

        // 检查房间是否可加入
        if (room.getGuest() != null) {
            throw new BusinessException(ErrorCode.ROOM_FULL.getCode(),
                    ErrorCode.ROOM_FULL.getMessage());
        }
        if (room.getStatus() != RoomStatus.WAITING) {
            throw new BusinessException(ErrorCode.ROOM_ALREADY_STARTED.getCode(),
                    ErrorCode.ROOM_ALREADY_STARTED.getMessage());
        }

        User guest = userRepository.findById(guestId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "用户不存在"));

        room.setGuest(guest);
        room = gameRoomRepository.save(room);

        return gameRoomMapper.toResponse(room);
    }

    /**
     * 如果房间还在等待中，将其取消。
     * 在 WebSocket 断开且房间无人时调用。
     */
    @Transactional
    public void cancelRoomIfWaiting(Long roomId) {
        GameRoom room = gameRoomRepository.findById(roomId).orElse(null);
        if (room != null && room.getStatus() == RoomStatus.WAITING) {
            room.setStatus(RoomStatus.CANCELLED);
            gameRoomRepository.save(room);
            log.info("Room {} cancelled (abandoned)", roomId);
        }
    }

    /**
     * 定时清理创建超过 30 分钟仍未开始的房间。
     * 每分钟执行一次，防止空房间堆积。
     */
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void cleanupStaleRooms() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(30);
        List<GameRoom> staleRooms = gameRoomRepository.findByStatusAndCreatedAtBefore(RoomStatus.WAITING, deadline);
        for (GameRoom room : staleRooms) {
            room.setStatus(RoomStatus.CANCELLED);
            log.info("Room {} cancelled by scheduled cleanup (created at {})", room.getId(), room.getCreatedAt());
        }
        gameRoomRepository.saveAll(staleRooms);
    }

    /** 获取房间信息 */
    public RoomResponse getRoom(Long roomId) {
        GameRoom room = gameRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND.getCode(),
                        ErrorCode.ROOM_NOT_FOUND.getMessage()));
        return gameRoomMapper.toResponse(room);
    }

    /** 生成唯一的 6 位房间码 */
    private String generateRoomCode() {
        StringBuilder code;
        do {
            code = new StringBuilder();
            for (int i = 0; i < ROOM_CODE_LENGTH; i++) {
                code.append(ROOM_CODE_CHARS.charAt(random.nextInt(ROOM_CODE_CHARS.length())));
            }
        } while (gameRoomRepository.existsByRoomCode(code.toString()));
        return code.toString();
    }
}
