-- 임시(6단계, ADR-0012): 친구 랭킹을 '목표 이하로 본 연속 일수'로, 친구 목록에 '지금 시청 중' 표시.
-- 연속 일수는 앱(core:insights)이 계산하고 서버는 저장만 한다.
ALTER TABLE standing ADD COLUMN streak_days INTEGER NOT NULL DEFAULT 0 CHECK (streak_days BETWEEN 0 AND 3650);

-- 지금 시청 중: 숏폼을 보는 동안 앱이 몇 초마다 보내는 신호의 마지막 시각만 남긴다(앱·영상·시간은 없음).
-- 신호를 멈추면 행을 지운다. 동의(버전 2 이상)를 철회하면 함께 지워진다.
CREATE TABLE presence (
    user_id TEXT PRIMARY KEY NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    last_seen_ms INTEGER NOT NULL
);
