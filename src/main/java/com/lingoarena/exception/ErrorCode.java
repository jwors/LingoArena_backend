package com.lingoarena.exception;

/**
 * 错误码枚举。
 *
 * 统一管理所有业务错误码和对应的中文错误消息。
 * 相比在 BusinessException 中硬编码字符串，用枚举的好处：
 * 1. 所有错误码集中定义，一目了然
 * 2. 避免拼写错误
 * 3. 前端可以根据 code 做国际化或特定处理
 */
public enum ErrorCode {

    // ===== 认证相关 =====
    EMAIL_ALREADY_EXISTS("EMAIL_ALREADY_EXISTS", "邮箱已被注册"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "邮箱或密码错误"),
    INVALID_TOKEN("INVALID_TOKEN", "无效的令牌"),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "令牌已过期"),

    // ===== 房间相关 =====
    ROOM_NOT_FOUND("ROOM_NOT_FOUND", "房间不存在"),
    ROOM_FULL("ROOM_FULL", "房间已满"),
    ROOM_ALREADY_STARTED("ROOM_ALREADY_STARTED", "游戏已开始"),
    INVALID_ROOM_CODE("INVALID_ROOM_CODE", "无效的房间码"),
    NOT_ROOM_HOST("NOT_ROOM_HOST", "不是房主"),

    // ===== 词库相关 =====
    WORDBOOK_NOT_FOUND("WORDBOOK_NOT_FOUND", "词库不存在"),

    // ===== 游戏相关 =====
    GAME_NOT_STARTED("GAME_NOT_STARTED", "游戏未开始"),
    NOT_YOUR_TURN("NOT_YOUR_TURN", "还没到你"),
    ROUND_ALREADY_ANSWERED("ROUND_ALREADY_ANSWERED", "本轮已作答"),

    // ===== 通用 =====
    VALIDATION_ERROR("VALIDATION_ERROR", "输入校验失败"),
    INTERNAL_ERROR("INTERNAL_ERROR", "服务器内部错误");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
}
