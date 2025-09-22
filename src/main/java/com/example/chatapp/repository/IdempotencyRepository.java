package com.example.chatapp.repository;

import com.example.chatapp.entity.IdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface IdempotencyRepository extends JpaRepository<IdempotencyKey, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdempotencyKey> findById(String id);
}