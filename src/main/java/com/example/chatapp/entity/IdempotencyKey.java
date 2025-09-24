package com.example.chatapp.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import org.apache.commons.codec.digest.DigestUtils;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdempotencyKey {

    @Id
    @Column(name = "idempotency_hash", length = 64, nullable = false)
    private String idempotencyHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public IdempotencyKey(String userId, String channelId, String clientMessageId) {
        this.idempotencyHash = generateHash(userId, channelId, clientMessageId);
        this.createdAt = LocalDateTime.now();
    }

    public static String generateHash(String userId, String channelId, String clientMessageId) {
        String input = userId + ":" + channelId + ":" + clientMessageId;
        return DigestUtils.sha256Hex(input);
    }
}