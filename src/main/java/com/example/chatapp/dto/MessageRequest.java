package com.example.chatapp.dto;

import com.example.chatapp.entity.Message;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MessageRequest {

    @NotBlank(message = "채널 ID는 필수입니다")
    private String channelId;

    @NotBlank(message = "사용자 ID는 필수입니다")
    private String userId;

    @NotBlank(message = "메시지 내용은 필수입니다")
    private String content;

    @NotNull(message = "메시지 타입은 필수입니다")
    private Message.MessageType messageType = Message.MessageType.CHAT;

    @NotBlank(message = "클라이언트 메시지 ID는 필수입니다")
    private String clientMessageId;
}