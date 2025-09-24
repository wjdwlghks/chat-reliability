package com.example.chatapp.repository;

import com.example.chatapp.entity.IdempotencyKey;
import jakarta.persistence.LockModeType;
import lombok.NonNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, String> {
    // 락이 필요한 경우를 위한 별도 메서드
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM IdempotencyKey i WHERE i.idempotencyKey = :key")
    Optional<IdempotencyKey> findByIdWithLock(String key);

}