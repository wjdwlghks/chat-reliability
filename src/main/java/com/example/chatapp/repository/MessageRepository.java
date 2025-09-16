package com.example.chatapp.repository;

import com.example.chatapp.entity.Message;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("SELECT COALESCE(MAX(m.sequenceNumber), 0) FROM Message m WHERE m.channelId = :channelId")
    Long findMaxSequenceNumberByChannelId(@Param("channelId") String channelId);
}