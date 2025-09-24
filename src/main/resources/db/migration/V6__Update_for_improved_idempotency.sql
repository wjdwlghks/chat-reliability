-- 개선된 멱등성 시스템을 위한 테이블 업데이트

-- 1. 기존 테이블들 삭제 (안전한 재시작)
DROP TABLE IF EXISTS idempotency_keys CASCADE;
DROP TABLE IF EXISTS messages CASCADE;

-- 2. messages 테이블 생성 (client_message_id 포함)
CREATE TABLE messages (
    id BIGSERIAL PRIMARY KEY,
    channel_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    client_message_id VARCHAR(255) NOT NULL,
    message_type VARCHAR(255) NOT NULL CHECK (message_type IN ('CHAT','JOIN','LEAVE')),
    sequence_number BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 3. idempotency_keys 테이블 생성 (간소화된 형태)
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. 인덱스들 추가
CREATE INDEX idx_messages_channel_sequence ON messages(channel_id, sequence_number);
CREATE INDEX idx_messages_client_message_id ON messages(user_id, channel_id, client_message_id);