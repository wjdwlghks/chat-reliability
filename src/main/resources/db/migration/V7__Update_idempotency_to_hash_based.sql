-- 해시 기반 멱등성 시스템으로 업데이트

-- 1. 기존 테이블이 있다면 삭제하고 새로 생성
DROP TABLE IF EXISTS idempotency_keys CASCADE;

-- 2. 해시 기반 idempotency_keys 테이블 생성
CREATE TABLE idempotency_keys (
    idempotency_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);