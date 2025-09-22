-- 멱등성 관리 테이블
CREATE TABLE idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED')),
    message_id BIGINT,
    failure_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- 만료된 키 정리를 위한 인덱스
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);

-- 상태별 조회를 위한 인덱스
CREATE INDEX idx_idempotency_keys_status ON idempotency_keys(status);

-- 메시지 ID로 조회를 위한 인덱스
CREATE INDEX idx_idempotency_keys_message_id ON idempotency_keys(message_id) WHERE message_id IS NOT NULL;