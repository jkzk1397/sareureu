-- 임시(5-2): 친구 서버를 새로 만드는 중이라 바뀔 수 있다(server/README.md).
-- 친구에게 보일 닉네임, 또래 통계용 연령대·신분, 친구에게 보일 공개 ID. 실명·연락처는 받지 않는다.
ALTER TABLE sharing_consent ADD COLUMN public_id TEXT;
ALTER TABLE sharing_consent ADD COLUMN nickname TEXT;
ALTER TABLE sharing_consent ADD COLUMN age_band TEXT;
ALTER TABLE sharing_consent ADD COLUMN occupation TEXT;
CREATE UNIQUE INDEX sharing_consent_by_public_id ON sharing_consent (public_id);

-- 랭킹용 내 숫자: 앱이 core:insights로 계산한 감소율·기록된 비율·유효한 날(ADR-0005). 친구에게는 이것만 보인다.
CREATE TABLE standing (
    user_id TEXT PRIMARY KEY NOT NULL REFERENCES sharing_consent(user_id) ON DELETE CASCADE,
    reduction_rate REAL CHECK (reduction_rate BETWEEN -10 AND 1),
    coverage REAL CHECK (coverage BETWEEN 0 AND 1),
    valid_days INTEGER NOT NULL CHECK (valid_days BETWEEN 0 AND 7),
    measured_at_ms INTEGER NOT NULL
);
