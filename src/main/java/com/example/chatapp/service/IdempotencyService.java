package com.example.chatapp.service;

import com.example.chatapp.exception.RedisException;
import com.example.chatapp.model.IdempotencyResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private static final String KEY_PREFIX = "idempotency:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);

    private final RedisTemplate<String, String> redisTemplate;

    public IdempotencyResult checkIdempotency(String userId, String channelId, String clientMessageId) {
        String key = buildKey(userId, channelId, clientMessageId);

        try {
            String value = redisTemplate.opsForValue().get(key);

            if (value == null) {
                return IdempotencyResult.newRequest();
            }

            return parseValue(value);

        } catch (Exception e) {
            log.error("Redis 멱등성 체크 실패: {}", e.getMessage(), e);
            // Redis 장애 시 새 요청으로 처리 (멱등성 비활성화)
            return IdempotencyResult.newRequest();
        }
    }

    public boolean markAsProcessing(String userId, String channelId, String clientMessageId) throws RedisException {
        String key = buildKey(userId, channelId, clientMessageId);

        try {
            // NX: key가 존재하지 않을 때만 설정 (atomic 연산)
            Boolean success = redisTemplate.opsForValue().setIfAbsent(key, "processing", DEFAULT_TTL);
            return Boolean.TRUE.equals(success);

        } catch (Exception e) {
            log.error("Redis 처리 중 마킹 실패: {}", e.getMessage(), e);
            throw new RedisException("Redis 연결 오류로 멱등성 처리 실패", e);
        }
    }

    public void markAsCompleted(String userId, String channelId, String clientMessageId, String messageId) {
        String key = buildKey(userId, channelId, clientMessageId);
        String value = "completed:" + messageId;

        try {
            redisTemplate.opsForValue().set(key, value, DEFAULT_TTL);

        } catch (Exception e) {
            log.error("Redis 완료 마킹 실패: {}, 멱등성 보장되지 않음", e.getMessage(), e);
            // 완료 마킹 실패는 시스템 기능에는 영향 없지만, 재시도 시 중복 처리될 수 있음
        }
    }

    public void markAsFailed(String userId, String channelId, String clientMessageId, String reason) {
        String key = buildKey(userId, channelId, clientMessageId);
        String value = "failed:" + reason;

        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofMinutes(5)); // 실패는 짧은 TTL

        } catch (Exception e) {
            log.error("Redis 실패 마킹 실패: {}", e.getMessage(), e);
        }
    }

    public void removeKey(String userId, String channelId, String clientMessageId) {
        String key = buildKey(userId, channelId, clientMessageId);

        try {
            redisTemplate.delete(key);

        } catch (Exception e) {
            log.error("Redis 키 삭제 실패: {}", e.getMessage(), e);
        }
    }

    private String buildKey(String userId, String channelId, String clientMessageId) {
        return KEY_PREFIX + userId + ":" + channelId + ":" + clientMessageId;
    }

    private IdempotencyResult parseValue(String value) {
        if ("processing".equals(value)) {
            return IdempotencyResult.processing();
        }

        if (value.startsWith("completed:")) {
            String messageId = value.substring("completed:".length());
            return IdempotencyResult.completed(messageId);
        }

        if (value.startsWith("failed:")) {
            String reason = value.substring("failed:".length());
            return IdempotencyResult.failed(reason);
        }

        // 알 수 없는 값은 새 요청으로 처리
        return IdempotencyResult.newRequest();
    }
}