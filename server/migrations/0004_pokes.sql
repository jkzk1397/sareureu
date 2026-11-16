-- 임시(5-4): 친구가 맡긴 잔소리. 받는 사람 앱이 가져가면 지운다. 푸시(FCM)는 쓰지 않는다.
CREATE TABLE poke (
    id TEXT PRIMARY KEY NOT NULL,
    from_user TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    to_user TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    text TEXT NOT NULL CHECK (length(text) BETWEEN 1 AND 40),
    created_at_ms INTEGER NOT NULL,
    expires_at_ms INTEGER NOT NULL
);
CREATE INDEX poke_by_to ON poke (to_user, created_at_ms);

-- 하루 한도용: 보낸 시각만 남긴다(내용·받는 사람 없음). 받은 잔소리를 지워도 한도는 그대로 센다. cron이 하루 지나면 지운다.
CREATE TABLE poke_sent (
    from_user TEXT NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    sent_at_ms INTEGER NOT NULL
);
CREATE INDEX poke_sent_by_from ON poke_sent (from_user, sent_at_ms);
