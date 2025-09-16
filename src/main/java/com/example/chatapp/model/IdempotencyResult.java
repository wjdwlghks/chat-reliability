package com.example.chatapp.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class IdempotencyResult {

    public enum Status {
        NEW,           // 새로운 요청
        PROCESSING,    // 처리 중
        COMPLETED,     // 처리 완료
        FAILED         // 처리 실패
    }

    private final Status status;
    private final String existingMessageId;
    private final String failureReason;

    public static IdempotencyResult newRequest() {
        return new IdempotencyResult(Status.NEW, null, null);
    }

    public static IdempotencyResult processing() {
        return new IdempotencyResult(Status.PROCESSING, null, null);
    }

    public static IdempotencyResult completed(String messageId) {
        return new IdempotencyResult(Status.COMPLETED, messageId, null);
    }

    public static IdempotencyResult failed(String reason) {
        return new IdempotencyResult(Status.FAILED, null, reason);
    }

    public boolean isNew() {
        return status == Status.NEW;
    }

    public boolean isProcessing() {
        return status == Status.PROCESSING;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }
}