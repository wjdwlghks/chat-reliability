package com.example.chatapp.controller;

import com.example.chatapp.dto.MessageRequest;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> createMessage(@Valid @RequestBody MessageRequest request) {
        log.info("메시지 생성 요청 - channelId: {}, userId: {}, clientMessageId: {}",
            request.getChannelId(), request.getUserId(), request.getClientMessageId());

        MessageResponse response = messageService.saveMessage(request);

        // 멱등성으로 인한 기존 메시지 반환인 경우 200 OK
        // 새로 생성된 메시지인 경우 201 Created
        boolean isNewMessage = response.getId() != null;
        HttpStatus status = isNewMessage ? HttpStatus.CREATED : HttpStatus.OK;

        log.info("메시지 생성 응답 - messageId: {}, status: {}", response.getId(), status);

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getMessages(
            @RequestParam String channelId,
            @RequestParam(required = false) Long afterSequence,
            @RequestParam(required = false) Long beforeSequence) {

        log.info("메시지 조회 요청 - channelId: {}, afterSequence: {}, beforeSequence: {}",
            channelId, afterSequence, beforeSequence);

        List<MessageResponse> messages = messageService.getMessages(channelId, null, afterSequence, beforeSequence);

        log.info("메시지 조회 응답 - channelId: {}, 메시지 수: {}", channelId, messages.size());

        return ResponseEntity.ok(messages);
    }
}