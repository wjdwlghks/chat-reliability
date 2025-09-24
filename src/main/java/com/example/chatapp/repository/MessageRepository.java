package com.example.chatapp.repository;

import com.example.chatapp.entity.Message;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) FROM Message m WHERE m.channelId = :channelId")
    Long findMaxSequenceNumberByChannelId(@Param("channelId") String channelId);

    @Query("SELECT m FROM Message m WHERE m.channelId = :channelId ORDER BY m.sequenceNumber DESC")
    List<Message> findByChannelIdOrderBySequenceNumberDesc(@Param("channelId") String channelId, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.channelId = :channelId AND m.sequenceNumber < :beforeSequence ORDER BY m.sequenceNumber DESC")
    List<Message> findByChannelIdAndSequenceNumberLessThan(@Param("channelId") String channelId,
                                                           @Param("beforeSequence") Long beforeSequence,
                                                           Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.channelId = :channelId AND m.sequenceNumber > :afterSequence ORDER BY m.sequenceNumber ASC")
    List<Message> findByChannelIdAndSequenceNumberGreaterThan(@Param("channelId") String channelId,
                                                             @Param("afterSequence") Long afterSequence,
                                                             Pageable pageable);

    Optional<Message> findByUserIdAndChannelIdAndClientMessageId(@Param("userId") String userId,
                                                               @Param("channelId") String channelId,
                                                               @Param("clientMessageId") String clientMessageId);
}