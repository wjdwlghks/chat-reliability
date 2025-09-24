package com.example.chatapp.repository;

import com.example.chatapp.entity.IdempotencyKey;
import jakarta.persistence.LockModeType;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, String> {

    // 기존 Pessimistic Write Lock 메서드
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyKey i WHERE i.idempotencyHash = :key")
    Optional<IdempotencyKey> findByIdWithLock(String key);

    // ON CONFLICT DO NOTHING을 위한 네이티브 쿼리
    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (idempotency_hash, created_at) VALUES (:hash, CURRENT_TIMESTAMP) ON CONFLICT (idempotency_hash) DO NOTHING",
           nativeQuery = true)
    int insertOnConflictDoNothing(@Param("hash") String hash);

    // Pessimistic Read Lock을 위한 메서드 (스레드2가 스레드1의 완료를 대기)
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT i FROM IdempotencyKey i WHERE i.idempotencyHash = :key")
    Optional<IdempotencyKey> findByHashWithReadLock(@Param("key") String key);

}