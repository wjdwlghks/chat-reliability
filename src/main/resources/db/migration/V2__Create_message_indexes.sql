-- 채널별 시퀀스 순서로 메시지를 효율적으로 조회하기 위한 인덱스
CREATE INDEX idx_messages_channel_sequence ON messages (channel_id, sequence_number);

-- 시간 기반 쿼리를 위한 인덱스
CREATE INDEX idx_messages_channel_created_at ON messages (channel_id, created_at);

-- 사용자별 메시지 조회를 위한 인덱스
CREATE INDEX idx_messages_user_channel ON messages (user_id, channel_id);

-- 채널별 시퀀스 번호 중복 방지를 위한 유니크 제약조건
CREATE UNIQUE INDEX idx_messages_channel_sequence_unique ON messages (channel_id, sequence_number);