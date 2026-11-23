// 친구 서버의 한도와 숫자를 한곳에 모은다(ADR-0014에 이유). 앱 쪽 짝은 core:social의 SocialLimits.
const secondMs = 1000;
const minuteMs = 60 * secondMs;
const dayMs = 86_400_000;

export const limits = Object.freeze({
    dayMs,

    // 초대 코드: 헷갈리는 글자(0·O·1·I·L)를 뺀 31글자, 8자리, 24시간, 한 번만.
    codeAlphabet: 'ABCDEFGHJKMNPQRSTUVWXYZ23456789',
    codeLength: 8,
    inviteTtlMs: dayMs,
    invitesPerDay: 10,

    // 틀린 코드: 계정마다 10분에 5번, 하루 20번.
    wrongCode: [{ windowMs: 10 * minuteMs, max: 5 }, { windowMs: dayMs, max: 20 }],

    // 연결 요청: 7일, 한 사람이 받는·보내는 대기 요청 20개씩.
    requestTtlMs: 7 * dayMs,
    maxPendingRequests: 20,
    maxFriends: 50,

    // 차단·끊기: 합쳐서 하루 50번.
    relationChangesPerDay: 50,

    // 잔소리: 40자, 7일, 하루 10번, 같은 친구에게 하루 3번, 한 번에 20개까지 내려 준다.
    pokeMaxLength: 40,
    pokeTtlMs: 7 * dayMs,
    pokesPerDay: 10,
    pokesPerFriendPerDay: 3,
    maxInbox: 20,

    // 지금 시청 중: 앱이 15초마다 신호를 보내고, 서버는 최근 40초 안에 신호가 있으면 시청 중으로 본다
    // (신호를 두 번 놓쳐도 이어지게). ADR-0012는 2초·5초였고 "사용자가 늘면 간격을 늘린다"고 했다(6-A).
    watchingWindowMs: 40 * secondMs,

    // 신고: 하루 10번. 최근 180일에 서로 다른 3명에게 신고되면 잔소리 보내기가 멈춘다.
    reportsPerDay: 10,
    reportersToSuspend: 3,
    reportRetentionMs: 180 * dayMs,

    nicknameMaxLength: 12,

    // 폰 시계가 서버보다 이만큼 앞서도 받는다. 넘으면 clock_skew.
    clockSkewMs: 5 * minuteMs,

    // 이 기간 업로드가 없는 계정은 동의 행과 딸린 기록을 지운다.
    abandonedAfterMs: 60 * dayMs,

    // 또래 통계 보호: 최근 14일 중 유효한 날(기록된 비율 80% 이상이거나 모름)이 7일 이상인 계정만 센다.
    peerWindowDays: 14,
    peerMinDays: 7,
    peerMinCoverage: 0.8,
    // 그룹 중앙값이 하루 사이 30%(적어도 15분) 넘게 바뀌면 그날은 갱신하지 않는다. 예전 값이 3일 넘게 묵었으면 받아들인다.
    peerJumpRatio: 0.3,
    peerJumpMinutes: 15,
    peerHoldMs: 3 * dayMs,

    // 앱이 X-App-Version으로 보내는 계약 번호. 헤더가 없으면 1(ADR-0014 이전 앱).
    minAppVersion: 1,
    // 이 번호부터 잔소리를 '확인(ack) 뒤 삭제'로 준다. 그 아래는 내려 주면 바로 지운다.
    ackFromVersion: 2,

    // IP 기준(Workers Rate Limiting 바인딩 IP_LIMITER가 있을 때만): 창은 wrangler.toml에서 60초에 20번.
    ipRetryAfterSec: 60,
});
