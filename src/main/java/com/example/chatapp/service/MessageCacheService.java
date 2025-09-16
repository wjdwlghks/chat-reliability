package com.example.chatapp.service;

import com.example.chatapp.dto.MessageResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageCacheService {

    private static final String CACHE_KEY_PREFIX = "message_cache:";

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void cacheMessage(String channelId, MessageResponse message) {
        try {
            String key = buildCacheKey(channelId);
            String serializedMessage = objectMapper.writeValueAsString(message);

            redisTemplate.opsForZSet().add(key, serializedMessage, message.getSequenceNumber());

            log.debug("메시지 캐시 저장 - channelId: {}, sequence: {}", channelId, message.getSequenceNumber());

        } catch (JsonProcessingException e) {
            log.error("메시지 직렬화 실패 - messageId: {}, 오류: {}", message.getId(), e.getMessage(), e);
        } catch (Exception e) {
            log.error("Redis 캐시 저장 실패 - channelId: {}, 오류: {}", channelId, e.getMessage(), e);
        }
    }

    public List<MessageResponse> getLatestMessages(String channelId, int limit) {
        try {
            String key = buildCacheKey(channelId);

            // ZSet에서 최신 메시지들을 내림차순으로 조회 (score 높은 순)
            Set<String> cachedMessages = redisTemplate.opsForZSet()
                .reverseRange(key, 0, limit - 1);

            if (cachedMessages == null || cachedMessages.isEmpty()) {
                log.debug("캐시 미스 - 최신 메시지 - channelId: {}", channelId);
                return null;
            }

            List<MessageResponse> messages = deserializeMessages(cachedMessages);
            log.debug("캐시 히트 - 최신 메시지 - channelId: {}, 조회된 메시지 수: {}", channelId, messages.size());

            return messages;

        } catch (Exception e) {
            log.error("Redis 캐시 조회 실패 - channelId: {}, 오류: {}", channelId, e.getMessage(), e);
            return null;
        }
    }

    public List<MessageResponse> getMessagesBefore(String channelId, Long beforeSequence, int limit) {
        try {
            String key = buildCacheKey(channelId);

            // beforeSequence 이전 메시지들을 내림차순으로 조회
            Set<String> cachedMessages = redisTemplate.opsForZSet()
                .reverseRangeByScore(key, Double.NEGATIVE_INFINITY, beforeSequence - 1, 0, limit);

            if (cachedMessages == null || cachedMessages.isEmpty()) {
                log.debug("캐시 미스 - beforeSequence: {} - channelId: {}", beforeSequence, channelId);
                return null;
            }

            List<MessageResponse> messages = deserializeMessages(cachedMessages);
            log.debug("캐시 히트 - beforeSequence: {} - channelId: {}, 조회된 메시지 수: {}",
                beforeSequence, channelId, messages.size());

            return messages;

        } catch (Exception e) {
            log.error("Redis 캐시 조회 실패 - channelId: {}, beforeSequence: {}, 오류: {}",
                channelId, beforeSequence, e.getMessage(), e);
            return null;
        }
    }

    public List<MessageResponse> getMessagesAfter(String channelId, Long afterSequence, int limit) {
        try {
            String key = buildCacheKey(channelId);

            // afterSequence 이후 메시지들을 오름차순으로 조회
            Set<String> cachedMessages = redisTemplate.opsForZSet()
                .rangeByScore(key, afterSequence + 1, Double.POSITIVE_INFINITY, 0, limit);

            if (cachedMessages == null || cachedMessages.isEmpty()) {
                log.debug("캐시 미스 - afterSequence: {} - channelId: {}", afterSequence, channelId);
                return null;
            }

            List<MessageResponse> messages = deserializeMessages(cachedMessages);
            log.debug("캐시 히트 - afterSequence: {} - channelId: {}, 조회된 메시지 수: {}",
                afterSequence, channelId, messages.size());

            return messages;

        } catch (Exception e) {
            log.error("Redis 캐시 조회 실패 - channelId: {}, afterSequence: {}, 오류: {}",
                channelId, afterSequence, e.getMessage(), e);
            return null;
        }
    }

    private String buildCacheKey(String channelId) {
        return CACHE_KEY_PREFIX + channelId;
    }

    private List<MessageResponse> deserializeMessages(Set<String> cachedMessages) {
        return cachedMessages.stream()
                .map(this::deserializeMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private MessageResponse deserializeMessage(String serializedMessage) {
        try {
            return objectMapper.readValue(serializedMessage, MessageResponse.class);
        } catch (JsonProcessingException e) {
            log.error("메시지 역직렬화 실패 - 데이터: {}, 오류: {}", serializedMessage, e.getMessage(), e);
            return null;
        }
    }
}