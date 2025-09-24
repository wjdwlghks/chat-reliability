-- 해시 기반 멱등성 시스템으로 업데이트

-- 1. 기존 idempotency_keys 테이블의 컬럼명을 idempotency_hash로 변경
ALTER TABLE idempotency_keys
RENAME COLUMN idempotency_key TO idempotency_hash;

-- 2. 컬럼 크기를 SHA-256 해시값에 맞게 64자로 변경
ALTER TABLE idempotency_keys
ALTER COLUMN idempotency_hash TYPE VARCHAR(64);