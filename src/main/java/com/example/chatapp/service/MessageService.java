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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final IdempotencyService idempotencyService;

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

            log.info("메시지 저장 완료 - messageId: {}, clientMessageId: {}, sequence: {}",
                savedMessage.getId(), clientMessageId, nextSequenceNumber);

            return new MessageResponse(savedMessage);

        } catch (Exception e) {
            // 5. 실패 마킹
            log.error("메시지 저장 실패 - clientMessageId: {}, 오류: {}", clientMessageId, e.getMessage(), e);
            idempotencyService.markAsFailed(userId, channelId, clientMessageId, e.getMessage());
            throw e;
        }
    }

    private Long getNextSequenceNumber(String channelId) {
        Long maxSequence = messageRepository.findMaxSequenceNumberByChannelId(channelId);
        return maxSequence + 1;
    }
}