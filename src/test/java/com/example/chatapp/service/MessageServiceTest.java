package com.example.chatapp.service;

import com.example.chatapp.dto.MessageRequest;
import com.example.chatapp.dto.MessageResponse;
import com.example.chatapp.entity.IdempotencyKey;
import com.example.chatapp.entity.Message;
import com.example.chatapp.repository.MessageRepository;
import com.example.chatapp.repository.IdempotencyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MessageServiceTest {

    @Autowired
    private MessageService messageService;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @MockBean
    private MessageCacheService messageCacheService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        idempotencyRepository.deleteAll();
    }

    @Test
    @DisplayName("정상적인 메시지 저장 및 응답 반환")
    void saveMessage_success() {
        // given
        MessageRequest request = new MessageRequest();
        request.setUserId("user1");
        request.setChannelId("channel1");
        request.setContent("안녕하세요");
        request.setClientMessageId("client-msg-1");
        request.setMessageType(Message.MessageType.CHAT);

        // when
        MessageResponse response = messageService.saveMessage(request);

        // then
        assertThat(response).isNotNull();
        assertThat(response.getUserId()).isEqualTo("user1");
        assertThat(response.getChannelId()).isEqualTo("channel1");
        assertThat(response.getContent()).isEqualTo("안녕하세요");
        assertThat(response.getMessageType()).isEqualTo(Message.MessageType.CHAT);
        assertThat(response.getSequenceNumber()).isEqualTo(1L);
        assertThat(response.getCreatedAt()).isNotNull();

        // DB에 실제로 저장되었는지 확인
        Message savedMessage = messageRepository.findById(response.getId()).orElse(null);
        assertThat(savedMessage).isNotNull();
        assertThat(savedMessage.getUserId()).isEqualTo("user1");
        assertThat(savedMessage.getChannelId()).isEqualTo("channel1");
        assertThat(savedMessage.getContent()).isEqualTo("안녕하세요");
        assertThat(savedMessage.getSequenceNumber()).isEqualTo(1L);
    }

    @Test
    @DisplayName("채널별 시퀀스 번호 순차 증가")
    void saveMessage_sequenceNumberIncrement() {
        // given
        String channelId = "channel1";
        String userId = "user1";

        // when & then
        // 첫 번째 메시지
        MessageRequest request1 = createMessageRequest(userId, channelId, "첫 번째 메시지", "client-msg-1");
        MessageResponse response1 = messageService.saveMessage(request1);
        assertThat(response1.getSequenceNumber()).isEqualTo(1L);

        // 두 번째 메시지
        MessageRequest request2 = createMessageRequest(userId, channelId, "두 번째 메시지", "client-msg-2");
        MessageResponse response2 = messageService.saveMessage(request2);
        assertThat(response2.getSequenceNumber()).isEqualTo(2L);

        // 세 번째 메시지
        MessageRequest request3 = createMessageRequest(userId, channelId, "세 번째 메시지", "client-msg-3");
        MessageResponse response3 = messageService.saveMessage(request3);
        assertThat(response3.getSequenceNumber()).isEqualTo(3L);
    }

    @Test
    @DisplayName("채널별 시퀀스 번호 독립성")
    void saveMessage_sequenceNumberIndependentByChannel() {
        // given
        String userId = "user1";
        String channel1 = "channel1";
        String channel2 = "channel2";

        // when & then
        // 채널1에서 메시지 저장
        MessageRequest request1 = createMessageRequest(userId, channel1, "채널1 메시지", "client-msg-1");
        MessageResponse response1 = messageService.saveMessage(request1);
        assertThat(response1.getSequenceNumber()).isEqualTo(1L);

        // 채널2에서 메시지 저장 (시퀀스는 1부터 시작)
        MessageRequest request2 = createMessageRequest(userId, channel2, "채널2 메시지", "client-msg-2");
        MessageResponse response2 = messageService.saveMessage(request2);
        assertThat(response2.getSequenceNumber()).isEqualTo(1L);

        // 채널1에서 다시 메시지 저장 (시퀀스는 2)
        MessageRequest request3 = createMessageRequest(userId, channel1, "채널1 메시지2", "client-msg-3");
        MessageResponse response3 = messageService.saveMessage(request3);
        assertThat(response3.getSequenceNumber()).isEqualTo(2L);

        // 채널2에서 다시 메시지 저장 (시퀀스는 2)
        MessageRequest request4 = createMessageRequest(userId, channel2, "채널2 메시지2", "client-msg-4");
        MessageResponse response4 = messageService.saveMessage(request4);
        assertThat(response4.getSequenceNumber()).isEqualTo(2L);
    }

    @Test
    @DisplayName("다양한 메시지 타입 저장")
    void saveMessage_differentMessageTypes() {
        // given
        String userId = "user1";
        String channelId = "channel1";

        // when & then
        // CHAT 메시지
        MessageRequest chatRequest = createMessageRequest(userId, channelId, "일반 채팅", "client-msg-1");
        chatRequest.setMessageType(Message.MessageType.CHAT);
        MessageResponse chatResponse = messageService.saveMessage(chatRequest);
        assertThat(chatResponse.getMessageType()).isEqualTo(Message.MessageType.CHAT);
        assertThat(chatResponse.getSequenceNumber()).isEqualTo(1L);

        // JOIN 메시지
        MessageRequest joinRequest = createMessageRequest(userId, channelId, "user1님이 입장했습니다", "client-msg-2");
        joinRequest.setMessageType(Message.MessageType.JOIN);
        MessageResponse joinResponse = messageService.saveMessage(joinRequest);
        assertThat(joinResponse.getMessageType()).isEqualTo(Message.MessageType.JOIN);
        assertThat(joinResponse.getSequenceNumber()).isEqualTo(2L);

        // LEAVE 메시지
        MessageRequest leaveRequest = createMessageRequest(userId, channelId, "user1님이 퇴장했습니다", "client-msg-3");
        leaveRequest.setMessageType(Message.MessageType.LEAVE);
        MessageResponse leaveResponse = messageService.saveMessage(leaveRequest);
        assertThat(leaveResponse.getMessageType()).isEqualTo(Message.MessageType.LEAVE);
        assertThat(leaveResponse.getSequenceNumber()).isEqualTo(3L);
    }

    @Test
    @DisplayName("동일한 clientMessageId로 중복 요청 시 기존 메시지 반환")
    void saveMessage_idempotency_duplicateRequest() {
        // given
        String userId = "user1";
        String channelId = "channel1";
        String clientMessageId = "duplicate-msg-1";
        String content = "중복 요청 테스트";

        MessageRequest request = createMessageRequest(userId, channelId, content, clientMessageId);

        // when - 첫 번째 요청 (별도 트랜잭션)
        MessageResponse firstResponse = transactionTemplate.execute(status -> {
            return messageService.saveMessage(request);
        });

        // when - 동일한 clientMessageId로 두 번째 요청 (별도 트랜잭션)
        MessageResponse secondResponse = transactionTemplate.execute(status -> {
            return messageService.saveMessage(request);
        });

        // then
        assertThat(firstResponse).isNotNull();
        assertThat(secondResponse).isNotNull();

        // 동일한 메시지 반환
        assertThat(secondResponse.getId()).isEqualTo(firstResponse.getId());
        assertThat(secondResponse.getUserId()).isEqualTo(firstResponse.getUserId());
        assertThat(secondResponse.getChannelId()).isEqualTo(firstResponse.getChannelId());
        assertThat(secondResponse.getContent()).isEqualTo(firstResponse.getContent());
        assertThat(secondResponse.getSequenceNumber()).isEqualTo(firstResponse.getSequenceNumber());
        assertThat(secondResponse.getCreatedAt()).isEqualTo(firstResponse.getCreatedAt());

        // DB에는 하나의 메시지만 저장되어야 함
        long messageCount = messageRepository.count();
        assertThat(messageCount).isEqualTo(1L);

        // 멱등키도 하나만 저장되어야 함
        long idempotencyCount = idempotencyRepository.count();
        assertThat(idempotencyCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("다른 clientMessageId는 서로 다른 메시지로 저장")
    void saveMessage_idempotency_differentClientMessageId() {
        // given
        String userId = "user1";
        String channelId = "channel1";
        String content = "다른 메시지";

        MessageRequest request1 = createMessageRequest(userId, channelId, content, "msg-1");
        MessageRequest request2 = createMessageRequest(userId, channelId, content, "msg-2");

        // when
        MessageResponse response1 = messageService.saveMessage(request1);
        MessageResponse response2 = messageService.saveMessage(request2);

        // then
        assertThat(response1.getId()).isNotEqualTo(response2.getId());
        assertThat(response1.getSequenceNumber()).isEqualTo(1L);
        assertThat(response2.getSequenceNumber()).isEqualTo(2L);

        // DB에 두 개의 메시지가 저장되어야 함
        long messageCount = messageRepository.count();
        assertThat(messageCount).isEqualTo(2L);

        // 멱등키도 두 개가 저장되어야 함
        long idempotencyCount = idempotencyRepository.count();
        assertThat(idempotencyCount).isEqualTo(2L);
    }

    @Test
    @DisplayName("같은 clientMessageId라도 다른 사용자/채널이면 다른 메시지로 저장")
    void saveMessage_idempotency_differentUserOrChannel() {
        // given
        String clientMessageId = "same-client-msg-id";
        String content = "같은 클라이언트 메시지 ID";

        MessageRequest request1 = createMessageRequest("user1", "channel1", content, clientMessageId);
        MessageRequest request2 = createMessageRequest("user2", "channel1", content, clientMessageId);
        MessageRequest request3 = createMessageRequest("user1", "channel2", content, clientMessageId);

        // when
        MessageResponse response1 = messageService.saveMessage(request1);
        MessageResponse response2 = messageService.saveMessage(request2);
        MessageResponse response3 = messageService.saveMessage(request3);

        // then
        assertThat(response1.getId()).isNotEqualTo(response2.getId());
        assertThat(response1.getId()).isNotEqualTo(response3.getId());
        assertThat(response2.getId()).isNotEqualTo(response3.getId());

        // 각각 다른 시퀀스 번호 (채널별로 독립적)
        assertThat(response1.getSequenceNumber()).isEqualTo(1L); // channel1
        assertThat(response2.getSequenceNumber()).isEqualTo(2L); // channel1
        assertThat(response3.getSequenceNumber()).isEqualTo(1L); // channel2

        // DB에 3개의 메시지가 저장되어야 함
        long messageCount = messageRepository.count();
        assertThat(messageCount).isEqualTo(3L);

        // 멱등키도 3개가 저장되어야 함 (user:channel:clientMessageId 조합이 다름)
        long idempotencyCount = idempotencyRepository.count();
        assertThat(idempotencyCount).isEqualTo(3L);
    }

    @Test
    @DisplayName("멱등키 저장 검증")
    void saveMessage_idempotency_keyStorage() {
        // given
        String userId = "user1";
        String channelId = "channel1";
        String clientMessageId = "test-msg-1";
        MessageRequest request = createMessageRequest(userId, channelId, "테스트 메시지", clientMessageId);

        // when
        MessageResponse response = messageService.saveMessage(request);

        // then
        String expectedIdempotencyHash = IdempotencyKey.generateHash(userId, channelId, clientMessageId);

        // 멱등키가 올바르게 저장되었는지 확인
        var idempotencyKey = idempotencyRepository.findById(expectedIdempotencyHash);
        assertThat(idempotencyKey).isPresent();
        assertThat(idempotencyKey.get().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("멱등성 검증 시 여러 번 호출해도 동일한 결과")
    void saveMessage_idempotency_multipleAttempts() {
        // given
        String userId = "user1";
        String channelId = "channel1";
        String clientMessageId = "multiple-test-1";
        MessageRequest request = createMessageRequest(userId, channelId, "다중 호출 테스트", clientMessageId);

        // when - 5번 연속 호출 (각각 별도 트랜잭션)
        MessageResponse response1 = transactionTemplate.execute(status -> messageService.saveMessage(request));
        MessageResponse response2 = transactionTemplate.execute(status -> messageService.saveMessage(request));
        MessageResponse response3 = transactionTemplate.execute(status -> messageService.saveMessage(request));
        MessageResponse response4 = transactionTemplate.execute(status -> messageService.saveMessage(request));
        MessageResponse response5 = transactionTemplate.execute(status -> messageService.saveMessage(request));

        // then - 모든 응답이 동일해야 함
        assertThat(response2.getId()).isEqualTo(response1.getId());
        assertThat(response3.getId()).isEqualTo(response1.getId());
        assertThat(response4.getId()).isEqualTo(response1.getId());
        assertThat(response5.getId()).isEqualTo(response1.getId());

        assertThat(response2.getSequenceNumber()).isEqualTo(response1.getSequenceNumber());
        assertThat(response3.getSequenceNumber()).isEqualTo(response1.getSequenceNumber());
        assertThat(response4.getSequenceNumber()).isEqualTo(response1.getSequenceNumber());
        assertThat(response5.getSequenceNumber()).isEqualTo(response1.getSequenceNumber());

        // DB에는 여전히 하나의 메시지만 있어야 함
        long messageCount = messageRepository.count();
        assertThat(messageCount).isEqualTo(1L);

        // 멱등키도 하나만 있어야 함
        long idempotencyCount = idempotencyRepository.count();
        assertThat(idempotencyCount).isEqualTo(1L);
    }

    private MessageRequest createMessageRequest(String userId, String channelId, String content, String clientMessageId) {
        MessageRequest request = new MessageRequest();
        request.setUserId(userId);
        request.setChannelId(channelId);
        request.setContent(content);
        request.setClientMessageId(clientMessageId);
        request.setMessageType(Message.MessageType.CHAT);
        return request;
    }

    @Test
    @DisplayName("동시에 여러 개의 동일한 요청이 들어와도 멱등성이 보장되고 데이터가 일관성을 유지한다")
    void saveMessage_concurrency_idempotencyTest() throws InterruptedException {
        // given
        int threadCount = 10; // 동시에 실행할 스레드 수
        // 스레드 풀 생성 (동시에 10개의 작업을 처리할 수 있도록)
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);

        // 모든 스레드가 준비될 때까지 대기하고, 동시에 시작하기 위한 Latch
        CountDownLatch startLatch = new CountDownLatch(1);
        // 모든 스레드가 작업을 마칠 때까지 대기하기 위한 Latch
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        String clientMessageId = "concurrent-msg-1";
        MessageRequest request = createMessageRequest("user1", "channel1", "동시성 테스트", clientMessageId);

        // 예외 발생 횟수를 세기 위한 Atomic 변수 (thread-safe)
        AtomicInteger exceptionCount = new AtomicInteger(0);

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    // 각각의 스레드가 자신만의 트랜잭션에서 작업을 수행하도록 수정
                    transactionTemplate.execute(status -> messageService.saveMessage(request));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    System.err.println("Exception captured: " + e.getMessage());
                    exceptionCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // 모든 스레드가 준비되었으므로, 래치를 열어 동시에 실행 시작!
        startLatch.countDown();
        // 모든 스레드가 끝날 때까지 대기 (최대 5초)
        finishLatch.await(5, TimeUnit.SECONDS);
        // 스레드 풀 종료
        executorService.shutdown();

        // then
        // 락이 정상적으로 동작했다면, 어떤 예외도 발생해서는 안 됨
        assertThat(exceptionCount.get()).isEqualTo(0);

        // 최종적으로 DB에는 단 하나의 메시지만 저장되어야 함
        long messageCount = messageRepository.count();
        assertThat(messageCount).isEqualTo(1L);

        // 멱등키도 단 하나만 저장되어야 함
        long idempotencyCount = idempotencyRepository.count();
        assertThat(idempotencyCount).isEqualTo(1L);
    }
}