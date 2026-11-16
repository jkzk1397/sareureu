-- 임시(5-5): 또래 비교용 그룹 집계. 매일 cron이 동의한 사용자의 하루 합계로 다시 계산한다.
-- 개인별 값은 저장하지 않는다(그룹마다 사람 수·중앙값·상위 25%만). 10명 미만 그룹은 만들지 않는다.
CREATE TABLE peer_stat (
    age_band TEXT NOT NULL,
    occupation TEXT NOT NULL,
    n INTEGER NOT NULL CHECK (n >= 10),
    p50 INTEGER NOT NULL,
    p75 INTEGER NOT NULL,
    computed_at_ms INTEGER NOT NULL,
    PRIMARY KEY (age_band, occupation)
);
