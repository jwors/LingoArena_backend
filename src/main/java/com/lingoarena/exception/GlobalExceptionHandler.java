package com.lingoarena.exception;

import com.lingoarena.dto.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * @RestControllerAdvice 是 Spring 提供的全局异常拦截机制。
 * 所有 Controller 抛出的异常都会经过这里，统一返回 JSON 格式的错误信息，
 * 而不是 Tomcat 默认的 HTML 错误页面。
 *
 * 处理流程：
 * 1. BusinessException -> 返回自定义错误码 + 消息
 * 2. MethodArgumentNotValidException -> 返回第一个校验失败的字段错误
 * 3. Exception（兜底）-> 返回 500 服务器内部错误
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e) {
        return ResponseEntity
                .status(e.getHttpStatus())
                .body(new ErrorResponse(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        // 从所有校验失败中取第一个字段的错误消息返回
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");
        return ResponseEntity
                .badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", message));
    }

    /** 兜底：所有未捕获的异常返回 500 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .internalServerError()
                .body(new ErrorResponse("INTERNAL_ERROR", "服务器内部错误"));
    }
}
