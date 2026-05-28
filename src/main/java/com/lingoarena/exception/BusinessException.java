package com.lingoarena.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * 业务异常类。
 * 继承 RuntimeException，在 Service 层抛出，由 GlobalExceptionHandler 统一处理。
 *
 * 和直接用 Exception 的区别：
 * - 自定义 code 字段：前端可以根据 code 做国际化或特定处理
 * - httpStatus 字段：不同错误可以返回不同的 HTTP 状态码
 * - 不用在每个 Controller 里写 try-catch，异常处理集中管理
 */
@Getter
public class BusinessException extends RuntimeException {
    /** 错误码（如 "EMAIL_ALREADY_EXISTS"），供前端判断具体错误类型 */
    private final String code;
    /** HTTP 状态码（如 400, 401, 404 等） */
    private final HttpStatus httpStatus;

    public BusinessException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    /** 默认使用 400 Bad Request */
    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }
}
