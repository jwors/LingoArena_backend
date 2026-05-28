package com.lingoarena.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 统一错误响应 DTO。
 * 所有异常最终都返回这个格式：{ "code": "XXX", "message": "xxx" }
 * 由 GlobalExceptionHandler 统一处理。
 */
@Data
@AllArgsConstructor
public class ErrorResponse {
    private String code;
    private String message;
}
