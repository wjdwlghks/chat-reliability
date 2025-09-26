##  빠른 시작

### 1. Docker로 실행 (권장)

```bash
# 전체 환경 실행 (PostgreSQL + Redis + Spring Boot)
docker-compose up -d

# 애플리케이션 준비 상태 확인
curl http://localhost:8080/actuator/health
```

### 2. 테스트 메시지 전송

```bash
# 메시지 생성
curl -X POST http://localhost:8080/messages \
  -H "Content-Type: application/json" \
  -d '{"channelId": "general", "userId": "user1", "content": "Hello World!", "messageType": "CHAT", "clientMessageId": "msg-001"}'

# 메시지 조회
curl "http://localhost:8080/messages?channelId=general"
```

### 3. 동시성 테스트

```bash
# 10개 동시 요청 (멱등성 검증)
for i in {1..10}; do
  curl -X POST http://localhost:8080/messages \
    -H "Content-Type: application/json" \
    -d '{"channelId": "test", "userId": "user1", "content": "동시성 테스트", "messageType": "CHAT", "clientMessageId": "same-id"}' &
done
wait

# 결과 확인: 1개 메시지만 저장되어야 함
curl "http://localhost:8080/messages?channelId=test"
```
## 📝 API 문서

### 메시지 생성
```http
POST /messages
Content-Type: application/json

{
  "channelId": "channel-1",
  "userId": "user-1",
  "content": "메시지 내용",
  "messageType": "CHAT",
  "clientMessageId": "unique-client-id"
}
```

### 메시지 조회
```http
# 최신 20개
GET /messages?channelId=channel-1

# 페이지네이션
GET /messages?channelId=channel-1&beforeSequence=100

# 스트림 복구
GET /messages?channelId=channel-1&afterSequence=50
```

## 🧪 테스트

```bash
# 전체 테스트
./gradlew test

# 동시성 테스트만
./gradlew test --tests "*concurrency*"
```