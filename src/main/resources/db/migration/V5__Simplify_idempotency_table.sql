-- 멱등성 테이블 단순화
DROP TABLE idempotency_keys;

CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    message_id BIGINT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 메시지 ID로 조회를 위한 인덱스
CREATE INDEX idx_idempotency_keys_message_id ON idempotency_keys(message_id);