-- 임시(5-3): 친구 연결. 팀원 서버가 바뀌면 달라질 수 있다(server/README.md).
-- 사람마다 살아 있는 초대 코드는 하나. 한 번 쓰면 지운다.
CREATE TABLE invite (
    code TEXT PRIMARY KEY NOT NULL,
    user_id TEXT NOT NULL UNIQUE REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    expires_at_ms INTEGER NOT NULL
);

-- 서로 동의한 친구(코드를 만든 사람 + 코드를 입력한 사람). 한 쌍은 한 행(user_a < user_b).
CREATE TABLE friendship (
    user_a TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    user_b TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    created_at_ms INTEGER NOT NULL,
    PRIMARY KEY (user_a, user_b),
    CHECK (user_a < user_b)
);
CREATE INDEX friendship_by_b ON friendship (user_b);

-- 차단: 연결을 끊고 다시 연결·잔소리를 막는다. 차단당한 쪽에는 알리지 않는다.
CREATE TABLE block (
    blocker TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    blocked TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    created_at_ms INTEGER NOT NULL,
    PRIMARY KEY (blocker, blocked)
);
CREATE INDEX block_by_blocked ON block (blocked);
