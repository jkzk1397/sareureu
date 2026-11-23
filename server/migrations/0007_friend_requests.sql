-- ADR-0014: 친구 연결을 '요청 뒤 수락'으로, 한도를 원자적으로 세기, 신고 저장.
-- 초대 코드는 해시만 둔다. 살아 있던 코드는 버린다(24시간짜리라 다시 만들면 된다).
DROP TABLE invite;
CREATE TABLE invite (
    code_hash TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL UNIQUE REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    expires_at_ms INTEGER NOT NULL
);

-- 연결 요청: 코드를 넣은 사람(from_user)이 코드 주인(to_user)에게. 주인이 수락해야 friendship이 된다.
-- 거절은 지우지 않고 rejected_at_ms만 적는다(요청한 쪽에는 만료될 때까지 '기다리는 중'으로 보여 거절을 알리지 않는다).
CREATE TABLE friend_request (
    id TEXT PRIMARY KEY NOT NULL,
    from_user TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    to_user TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    created_at_ms INTEGER NOT NULL,
    expires_at_ms INTEGER NOT NULL,
    rejected_at_ms INTEGER,
    UNIQUE (from_user, to_user),
    CHECK (from_user <> to_user)
);
CREATE INDEX friend_request_by_to ON friend_request (to_user, expires_at_ms);

-- 한도용 사건 기록: 누가(kind) 언제 했는지만 남긴다. 하루 지나면 cron이 지운다.
-- kind 예: code_try(틀린 코드), invite(코드 만들기), relation(차단·끊기), poke, poke>{받는 사람}, report.
CREATE TABLE rate_event (
    user_id TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    at_ms INTEGER NOT NULL
);
CREATE INDEX rate_event_by_user ON rate_event (user_id, kind, at_ms);
DROP TABLE poke_sent;

-- 같은 요청이 두 번 와도 한 번만 처리하려는 멱등 키(잔소리 보내기). 하루 지나면 cron이 지운다.
CREATE TABLE idempotency (
    user_id TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    key TEXT NOT NULL,
    result TEXT NOT NULL,
    at_ms INTEGER NOT NULL,
    PRIMARY KEY (user_id, key)
);

-- 신고: 운영자가 D1에서 본다. 서로 다른 3명에게 신고된 계정은 잔소리 보내기가 멈춘다.
-- text는 신고한 잔소리 문장(없을 수 있음). 180일 지나면 cron이 지운다. 어느 쪽이든 동의를 철회하면 함께 지워진다.
CREATE TABLE report (
    id TEXT PRIMARY KEY NOT NULL,
    reporter TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    target TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    reason TEXT NOT NULL,
    text TEXT CHECK (text IS NULL OR length(text) BETWEEN 1 AND 40),
    created_at_ms INTEGER NOT NULL,
    CHECK (reporter <> target)
);
CREATE INDEX report_by_target ON report (target, created_at_ms);
