package com.example.chatapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public IdempotencyKey(String idempotencyKey, Long messageId) {
        this.idempotencyKey = idempotencyKey;
        this.messageId = messageId;
        this.createdAt = LocalDateTime.now();
    }
}