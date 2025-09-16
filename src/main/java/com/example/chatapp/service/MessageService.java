package com.example.chatapp.service;

import com.example.chatapp.dto.MessageRequest;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.entity.Message;
import com.example.chatapp.exception.ConflictException;
import com.example.chatapp.exception.RedisException;
import com.example.chatapp.model.IdempotencyResult;
import com.example.chatapp.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final IdempotencyService idempotencyService;
    private final MessageCacheService messageCacheService;

    @Transactional
    public MessageResponse saveMessage(MessageRequest request) {
        String userId = request.getUserId();
        String channelId = request.getChannelId();
        String clientMessageId = request.getClientMessageId();

        // 1. 멱등성 체크
        IdempotencyResult result = idempotencyService.checkIdempotency(userId, channelId, clientMessageId);

        if (result.isProcessing()) {
            throw new ConflictException("메시지 처리 중입니다. 잠시 후 다시 시도해주세요.");
        }

        if (result.isCompleted()) {
            // 기존 메시지 반환
            Long existingMessageId = Long.parseLong(result.getExistingMessageId());
            Message existingMessage = messageRepository.findById(existingMessageId)
                .orElseThrow(() -> new IllegalStateException("완료된 메시지를 찾을 수 없습니다: " + existingMessageId));

            log.info("멱등성 검증: 기존 메시지 반환 - messageId: {}, clientMessageId: {}",
                existingMessageId, clientMessageId);
            return new MessageResponse(existingMessage);
        }

        if (result.isFailed()) {
            // 실패 키 삭제하고 재처리
            log.info("이전 실패한 요청 재처리 - clientMessageId: {}, 실패 사유: {}",
                clientMessageId, result.getFailureReason());
            idempotencyService.removeKey(userId, channelId, clientMessageId);
        }

        // 2. 처리 시작 마킹
        try {
            if (!idempotencyService.markAsProcessing(userId, channelId, clientMessageId)) {
                throw new ConflictException("동시 요청이 감지되었습니다. 다시 시도해주세요.");
            }
        } catch (RedisException e) {
            log.error("Redis 연결 오류로 멱등성 처리 실패: {}", e.getMessage(), e);
            throw new RuntimeException("시스템 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", e);
        }

        try {
            // 3. 메시지 저장
            Long nextSequenceNumber = getNextSequenceNumber(channelId);

            Message message = new Message(
                channelId,
                userId,
                request.getContent(),
                request.getMessageType()
            );
            message.setSequenceNumber(nextSequenceNumber);

            Message savedMessage = messageRepository.save(message);

            // 4. 완료 마킹
            idempotencyService.markAsCompleted(userId, channelId, clientMessageId, savedMessage.getId().toString());

            // 5. 캐시에 저장
            MessageResponse response = new MessageResponse(savedMessage);
            messageCacheService.cacheMessage(channelId, response);

            log.info("메시지 저장 완료 - messageId: {}, clientMessageId: {}, sequence: {}",
                savedMessage.getId(), clientMessageId, nextSequenceNumber);

            return response;

        } catch (Exception e) {
            // 5. 실패 마킹
            log.error("메시지 저장 실패 - clientMessageId: {}, 오류: {}", clientMessageId, e.getMessage(), e);
            idempotencyService.markAsFailed(userId, channelId, clientMessageId, e.getMessage());
            throw e;
        }
    }

    public List<MessageResponse> getMessages(String channelId, Integer limit, Long afterSequence, Long beforeSequence) {
        log.info("메시지 조회 요청 - channelId: {}, limit: {}, afterSequence: {}, beforeSequence: {}",
            channelId, limit, afterSequence, beforeSequence);

        // 기본값 설정 (고정 20개)
        int pageSize = 20;

        // 1. 캐시에서 먼저 조회 시도
        List<MessageResponse> cachedMessages = getCachedMessages(channelId, afterSequence, beforeSequence, pageSize);

        // 2. 캐시에서 충분한 메시지를 가져온 경우
        if (cachedMessages != null && cachedMessages.size() >= pageSize) {
            log.info("캐시에서 충분한 메시지 조회 완료 - channelId: {}, 조회된 메시지 수: {}", channelId, cachedMessages.size());
            return cachedMessages.subList(0, pageSize); // limit 수만큼 반환
        }

        // 3. 캐시 미스이거나 부족한 경우 - 하이브리드 조회
        return getHybridMessages(channelId, afterSequence, beforeSequence, pageSize, cachedMessages);
    }

    private List<MessageResponse> getCachedMessages(String channelId, Long afterSequence, Long beforeSequence, int pageSize) {
        try {
            if (afterSequence != null) {
                return messageCacheService.getMessagesAfter(channelId, afterSequence, pageSize);
            } else if (beforeSequence != null) {
                return messageCacheService.getMessagesBefore(channelId, beforeSequence, pageSize);
            } else {
                return messageCacheService.getLatestMessages(channelId, pageSize);
            }
        } catch (Exception e) {
            log.warn("캐시 조회 중 오류 발생 - channelId: {}, 오류: {}", channelId, e.getMessage());
            return null;
        }
    }

    private List<MessageResponse> getHybridMessages(String channelId, Long afterSequence, Long beforeSequence,
                                                  int pageSize, List<MessageResponse> cachedMessages) {

        int cachedCount = cachedMessages != null ? cachedMessages.size() : 0;
        int remainingCount = pageSize - cachedCount;

        log.debug("하이브리드 조회 시작 - channelId: {}, 캐시된 메시지: {}개, 추가 필요: {}개",
                 channelId, cachedCount, remainingCount);

        // DB에서 추가 메시지 조회
        List<MessageResponse> dbMessages = getAdditionalMessagesFromDB(
            channelId, afterSequence, beforeSequence, remainingCount, cachedMessages);

        // 캐시된 메시지와 DB 메시지 병합
        List<MessageResponse> result = mergeMessages(cachedMessages, dbMessages, afterSequence);

        // 새로 조회한 DB 메시지들을 캐시에 저장
        cacheNewMessages(channelId, dbMessages, afterSequence, beforeSequence);

        log.info("하이브리드 조회 완료 - channelId: {}, 총 메시지 수: {} (캐시: {}개, DB: {}개)",
                channelId, result.size(), cachedCount, dbMessages.size());

        return result;
    }

    private List<MessageResponse> getAdditionalMessagesFromDB(String channelId, Long afterSequence, Long beforeSequence,
                                                            int remainingCount, List<MessageResponse> cachedMessages) {

        Pageable pageable = PageRequest.of(0, remainingCount);
        List<Message> messages;

        if (afterSequence != null) {
            // 스트림 복구: 캐시된 메시지의 최대 시퀀스 이후부터 조회
            Long lastCachedSequence = getLastSequenceFromCache(cachedMessages, true);
            Long startSequence = lastCachedSequence != null ? lastCachedSequence : afterSequence;
            messages = messageRepository.findByChannelIdAndSequenceNumberGreaterThan(channelId, startSequence, pageable);

        } else if (beforeSequence != null) {
            // 페이지네이션: 캐시된 메시지의 최소 시퀀스 이전부터 조회
            Long lastCachedSequence = getLastSequenceFromCache(cachedMessages, false);
            Long endSequence = lastCachedSequence != null ? lastCachedSequence : beforeSequence;
            messages = messageRepository.findByChannelIdAndSequenceNumberLessThan(channelId, endSequence, pageable);

        } else {
            // 최신 메시지: 캐시된 메시지의 최소 시퀀스 이전부터 조회
            Long lastCachedSequence = getLastSequenceFromCache(cachedMessages, false);
            if (lastCachedSequence != null) {
                messages = messageRepository.findByChannelIdAndSequenceNumberLessThan(channelId, lastCachedSequence, pageable);
            } else {
                messages = messageRepository.findByChannelIdOrderBySequenceNumberDesc(channelId, pageable);
            }
        }

        return messages.stream()
                .map(MessageResponse::new)
                .collect(Collectors.toList());
    }

    private Long getLastSequenceFromCache(List<MessageResponse> cachedMessages, boolean isAfterSequence) {
        if (cachedMessages == null || cachedMessages.isEmpty()) {
            return null;
        }

        if (isAfterSequence) {
            // afterSequence의 경우 캐시된 메시지 중 가장 큰 시퀀스 반환
            return cachedMessages.stream()
                    .mapToLong(MessageResponse::getSequenceNumber)
                    .max()
                    .orElse(0L);
        } else {
            // beforeSequence나 최신 메시지의 경우 캐시된 메시지 중 가장 작은 시퀀스 반환
            return cachedMessages.stream()
                    .mapToLong(MessageResponse::getSequenceNumber)
                    .min()
                    .orElse(Long.MAX_VALUE);
        }
    }

    private List<MessageResponse> mergeMessages(List<MessageResponse> cachedMessages, List<MessageResponse> dbMessages,
                                              Long afterSequence) {

        List<MessageResponse> result = new ArrayList<>();

        if (cachedMessages != null) {
            result.addAll(cachedMessages);
        }
        result.addAll(dbMessages);

        // 정렬 및 중복 제거
        if (afterSequence != null) {
            // 스트림 복구: 시퀀스 오름차순 정렬
            return result.stream()
                    .sorted(Comparator.comparing(MessageResponse::getSequenceNumber))
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            // 페이지네이션/최신 메시지: 시퀀스 내림차순 정렬
            return result.stream()
                    .sorted(Comparator.comparing(MessageResponse::getSequenceNumber).reversed())
                    .distinct()
                    .collect(Collectors.toList());
        }
    }

    private void cacheNewMessages(String channelId, List<MessageResponse> newMessages, Long afterSequence, Long beforeSequence) {
        if (newMessages.isEmpty()) {
            return;
        }

        try {
            newMessages.forEach(message -> messageCacheService.cacheMessage(channelId, message));
            log.debug("DB 조회 결과 캐시 저장 완료 - channelId: {}, 저장된 메시지 수: {}", channelId, newMessages.size());
        } catch (Exception e) {
            log.warn("캐시 저장 중 오류 발생 - channelId: {}, 오류: {}", channelId, e.getMessage());
        }
    }

    private Long getNextSequenceNumber(String channelId) {
        Long maxSequence = messageRepository.findMaxSequenceNumberByChannelId(channelId);
        return maxSequence + 1;
    }
}