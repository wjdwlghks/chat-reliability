CREATE TABLE channel_offsets (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    channel_id VARCHAR(255) NOT NULL,
    last_read_sequence BIGINT NOT NULL DEFAULT 0,
    last_read_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 유니크 제약조건: 사용자별 채널당 하나의 오프셋 레코드만 허용
CREATE UNIQUE INDEX idx_channel_offsets_user_channel ON channel_offsets (user_id, channel_id);

-- 채널 기반 쿼리를 위한 인덱스
CREATE INDEX idx_channel_offsets_channel ON channel_offsets (channel_id);

-- 사용자 기반 쿼리를 위한 인덱스
CREATE INDEX idx_channel_offsets_user ON channel_offsets (user_id);