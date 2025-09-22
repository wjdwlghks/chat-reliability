package com.example.chatapp.controller;

import com.example.chatapp.dto.MessageRequest;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.entity.Message;
import com.example.chatapp.service.MessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.ZonedDateTime;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(MessageRequest messageRequest) {
        log.info("웹소켓 메시지 수신 - channelId: {}, userId: {}, content: {}",
            messageRequest.getChannelId(), messageRequest.getUserId(), messageRequest.getContent());

        try {
            // 메시지 저장 (멱등성 보장)
            MessageResponse response = messageService.saveMessage(messageRequest);

            // 특정 채널 구독자들에게만 브로드캐스트
            messagingTemplate.convertAndSend(
                "/topic/channel/" + messageRequest.getChannelId(),
                response
            );

        } catch (Exception e) {
            log.error("웹소켓 메시지 처리 실패: {}", e.getMessage(), e);
        }
    }

    @MessageMapping("/chat.addUser")
    public void addUser(MessageRequest messageRequest) {
        log.info("사용자 입장 - channelId: {}, userId: {}",
            messageRequest.getChannelId(), messageRequest.getUserId());

        // 임시 알림 메시지 생성 (DB 저장 안함)
        MessageResponse notificationResponse = new MessageResponse();
        notificationResponse.setUserId(messageRequest.getUserId());
        notificationResponse.setChannelId(messageRequest.getChannelId());
        notificationResponse.setContent(messageRequest.getUserId() + "님이 입장했습니다.");
        notificationResponse.setMessageType(Message.MessageType.JOIN);
        notificationResponse.setCreatedAt(ZonedDateTime.now());

        // 실시간 알림만 전송 (저장하지 않음)
        messagingTemplate.convertAndSend(
            "/topic/channel/" + messageRequest.getChannelId(),
            notificationResponse
        );

        log.info("사용자 입장 알림 전송 완료 - userId: {}, channelId: {}",
            messageRequest.getUserId(), messageRequest.getChannelId());
    }

    @MessageMapping("/chat.removeUser")
    public void removeUser(MessageRequest messageRequest) {
        log.info("사용자 퇴장 - channelId: {}, userId: {}",
            messageRequest.getChannelId(), messageRequest.getUserId());

        // 임시 알림 메시지 생성 (DB 저장 안함)
        MessageResponse notificationResponse = new MessageResponse();
        notificationResponse.setUserId(messageRequest.getUserId());
        notificationResponse.setChannelId(messageRequest.getChannelId());
        notificationResponse.setContent(messageRequest.getUserId() + "님이 퇴장했습니다.");
        notificationResponse.setMessageType(Message.MessageType.LEAVE);
        notificationResponse.setCreatedAt(ZonedDateTime.now());

        // 실시간 알림만 전송 (저장하지 않음)
        messagingTemplate.convertAndSend(
            "/topic/channel/" + messageRequest.getChannelId(),
            notificationResponse
        );

        log.info("사용자 퇴장 알림 전송 완료 - userId: {}, channelId: {}",
            messageRequest.getUserId(), messageRequest.getChannelId());
    }
}