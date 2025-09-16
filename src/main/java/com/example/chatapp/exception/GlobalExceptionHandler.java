package com.example.chatapp.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, String>> handleConflictException(ConflictException e) {
        log.warn("Conflict 오류: {}", e.getMessage());

        Map<String, String> response = new HashMap<>();
        response.put("error", "CONFLICT");
        response.put("message", e.getMessage());

        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException e) {
        log.warn("Validation 오류: {}", e.getMessage());

        Map<String, Object> response = new HashMap<>();
        Map<String, String> fieldErrors = new HashMap<>();

        e.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            fieldErrors.put(fieldName, errorMessage);
        });

        response.put("error", "VALIDATION_FAILED");
        response.put("message", "요청 데이터가 유효하지 않습니다");
        response.put("fieldErrors", fieldErrors);

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(RedisException.class)
    public ResponseEntity<Map<String, String>> handleRedisException(RedisException e) {
        log.error("Redis 연결 오류: {}", e.getMessage(), e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "SERVICE_UNAVAILABLE");
        response.put("message", "시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.");

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleIllegalStateException(IllegalStateException e) {
        log.error("상태 오류: {}", e.getMessage(), e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "INTERNAL_ERROR");
        response.put("message", "내부 상태 오류가 발생했습니다");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception e) {
        log.error("예상치 못한 오류: {}", e.getMessage(), e);

        Map<String, String> response = new HashMap<>();
        response.put("error", "INTERNAL_ERROR");
        response.put("message", "서버 내부 오류가 발생했습니다");

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}