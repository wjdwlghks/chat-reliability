package com.example.chatapp.dto;

import com.example.chatapp.entity.Message;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
public class MessageResponse {

    private Long id;
    private String channelId;
    private String userId;
    private String content;
    private Message.MessageType messageType;
    private Long sequenceNumber;
    private ZonedDateTime createdAt;

    public MessageResponse(Message message) {
        this.id = message.getId();
        this.channelId = message.getChannelId();
        this.userId = message.getUserId();
        this.content = message.getContent();
        this.messageType = message.getMessageType();
        this.sequenceNumber = message.getSequenceNumber();
        this.createdAt = message.getCreatedAt();
    }
}