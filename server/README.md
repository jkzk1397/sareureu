# 일일 시청 합계 수집 Worker

안드로이드 앱이 기기에서 계산한 숏폼 시청 분과 기록률을 D1에 저장한다. 날짜는 앱과 같은 현지 시각 새벽 4시를 기준으로 하고, 서버는 수신·저장 시각을 UTC로 기록한다. 앱의 세션 시작·종료 시각, 앱 이름, 원시 이벤트는 보내지 않는다.

## Cloudflare 설정

1. `server` 폴더에서 `npx wrangler login`을 실행한다. 기존 D1이 있으면 `npx wrangler d1 list`로 확인하고, 없으면 `npx wrangler d1 create sareureu-db`로 만든다.
2. `wrangler.toml`에 실제 `database_name`, `database_id`, `FIREBASE_PROJECT_ID`를 입력한다. Firebase 프로젝트 ID는 안드로이드에서 로그인에 사용할 프로젝트와 같아야 한다.
3. `server` 폴더에서 `npx wrangler d1 migrations apply DB --remote`를 한 번 실행한다. 로컬 검증에는 `--local`을 쓴다.
4. Cloudflare의 `sareureu` Worker에서 **Settings → Builds → Root directory**를 `server`로 지정한다. **Build command**는 비워 두고 **Deploy command**는 `npx wrangler deploy`로 설정한다. 설정과 코드가 배포 대상 브랜치에 있으면 빌드를 다시 실행한다.
5. Worker의 `GET /health`가 `204`를 돌려주면 D1 바인딩과 테이블이 연결된 것이다.

## 지금 배포 상태 (2026-11-16)

- Worker `sareureu`는 팀원 Cloudflare 계정에 있고 리드 계정이 공동 작업자다. GitHub 빌드가 이 저장소 `main`에 연결돼 있어 **`server/`를 고친 PR이 `main`에 머지되면 자동 배포**된다. 따로 `wrangler deploy`를 하지 않는다.
- `FIREBASE_PROJECT_ID`는 앱의 Firebase 프로젝트(`app/google-services.json`의 `project_id`)와 같아야 한다. 다르면 모든 요청이 `401 invalid_token`이다.
- 앱이 부를 주소는 코드에 쓰지 않고 각자 `local.properties`의 `socialBaseUrl`에 둔다(없으면 가상 친구로 동작).

## 앱 연결 계약

- 아래 요청에는 `Authorization: Bearer <Firebase ID token>`을 넣는다. 서버는 토큰을 검증해서 사용자 ID를 얻는다. JSON 요청은 `Content-Type: application/json`을 사용한다.
- 사용자가 친구 랭킹용 일일 집계 전송에 명시적으로 동의하면 `PUT /api/v1/sharing`에 `{"consentVersion":1}`을 보내고 `204`를 확인한다. 앱에도 버전과 동의 시각을 보관한다.
- 동의한 실제 데이터만 `PUT /api/v1/daily/{YYYY-MM-DD}`로 전송한다. `DayBoundary.logicalDate`와 `ShortformMinutes.onDay`를 재사용한다. 시연 데이터는 전송하지 않는다. 기록률을 아직 측정하지 못했다면 `coverage`는 `null`로 보낸다.
- 아래 예시의 `measuredAtMs`는 **합계를 계산한 시각의 유닉스 밀리초**로 바꾼다. 재시도할 때는 동일한 값을 사용한다. 동일 사용자·날짜에는 가장 최근에 측정한 합계 한 건만 유지된다.

```json
{
  "shortformMinutes": 37,
  "coverage": null,
  "measuredAtMs": 1794272400000,
  "timeZone": "Asia/Seoul"
}
```

- 동의를 철회하면 업로드를 중단하고 대기 중인 업로드를 취소한 뒤 `DELETE /api/v1/sharing`을 호출한다. 서버는 해당 사용자의 일일 합계를 함께 삭제한다. 365일보다 오래된 집계는 예약 작업으로 삭제한다.
- 앱은 `core:social`의 `RemoteSharingRepository`(동의·철회)와 `SocialUploadWorker`(하루 1번)로 이 계약을 쓴다. 서버 주소가 없거나 시연 모드면 부르지 않는다.

## 임시로 넣은 것 (5단계)

> 팀원이 서버를 새로 만들고 있어서 **아래는 모두 임시**다. 바뀌면 앱의 `core:social/remote`만 고친다.

| 경로 | 무엇 | 단계 |
|---|---|---|
| `PUT /api/v1/sharing` | 동의 몸통에 `nickname`(12자까지, 친구에게 보임), `ageBand`·`occupation`(enum 이름, 또래 그룹용)을 더 받는다(모두 선택). 다른 칸은 400 | 5-2 |
| `PUT /api/v1/standing` | `{"reductionRate":0.23,"coverage":0.9,"validDays":6,"measuredAtMs":…}` 랭킹 숫자. 계산은 앱(`core:insights`)이 하고 서버는 저장만. 동의 전이면 403 | 5-2 |
| `POST /api/v1/invites` | 내 초대 코드(8자, 헷갈리는 글자 0·O·1·I·L을 뺀 31글자에서 고르게) `{"code","expiresAtMs"}`. 24시간, 한 번만, 새로 만들면 이전 코드는 없어진다. 서버에는 해시만 둔다. 하루 10번 | ADR-0014 |
| `DELETE /api/v1/invites` | 내 코드 취소 | ADR-0014 |
| `POST /api/v1/invites/{code}/requests` | 코드로 **연결 요청**. `201 {"request":{"id","expiresAtMs"}}`(주인의 닉네임·숫자는 주지 않는다). 없는 코드·만료·차단·주인의 대기 요청 20개는 모두 `404 invite_not_found`. `409 own_invite`·`already_friends`·`too_many_requests`(내가 보낸 대기 요청 20개). 틀린 코드는 계정마다 10분에 5번·하루 20번 → `429 too_many_attempts` + `Retry-After` | ADR-0014 |
| `GET /api/v1/friend-requests` | `{"received":[{"id","fromNickname","createdAtMs","expiresAtMs"}],"sent":[{"id","createdAtMs","expiresAtMs"}]}`. 받은 쪽에는 요청한 사람의 닉네임만, 보낸 쪽에는 상대가 누구인지 없다. 거절된 요청도 보낸 쪽에는 만료(7일) 전까지 그대로 보인다 | ADR-0014 |
| `POST /api/v1/friend-requests/{id}/accept` | 코드 주인이 수락 → `201 {"friend":{"id","nickname"}}`. `404 request_not_found`(없음·만료·거절함·차단), `409 too_many_friends`(어느 쪽이든 50명) | ADR-0014 |
| `DELETE /api/v1/friend-requests/{id}` | 받은 사람이면 거절(상대에게 알리지 않음), 보낸 사람이면 취소. `?block=1`이면 거절하면서 차단. 언제나 204 | ADR-0014 |
| `POST /api/v1/invites/{code}/accept` | **없어짐.** 옛 앱이 부르면 `426 upgrade_required` | ADR-0014 |
| `GET /api/v1/friends` | `{"friends":[{"id","nickname","reductionRate","coverage","validDays"}],"blocked":[{"id","nickname"}]}`. `id`는 공개 ID(Firebase uid 아님). 순위·꼴찌 숨기기는 앱이 `Ranking.build`로 | 5-3 |
| `DELETE /api/v1/friends/{id}` | 연결 끊기(양쪽 목록에서 빠짐) | 5-3 |
| `PUT/DELETE /api/v1/blocks/{id}` | 차단(연결도 끊음)·차단 풀기(연결은 되살리지 않음) | 5-3 |
| `POST /api/v1/pokes` | `{"to":"<친구 공개 ID>","text":"40자까지"}` 잔소리 보내기. 친구에게만(`403 not_friends`), 시청 중일 때만(`409 not_watching`), 하루 10개·같은 친구에게 3개(`429 daily_limit` + `Retry-After`). 서버가 글을 다듬고(NFC, 줄바꿈·제어 문자·폭 없는 문자 제거) 금지 표현을 막는다(`400 banned_text`). 3명에게 신고된 계정은 `403 suspended`. `Idempotency-Key` 헤더를 붙이면 같은 요청이 두 번 와도 한 번만 처리하고 같은 `id`를 돌려준다 | 5-4, ADR-0014 |
| `GET /api/v1/pokes` | 나에게 온 잔소리 `{"pokes":[{"id","fromId","fromNickname","text","createdAtMs","expiresAtMs"}]}`(20개까지). 계약 번호 2 이상이면 **받았다고 확인할 때까지 지우지 않는다**: 받은 id를 `?ack=id1,id2`(또는 시청 신호의 `ack`)로 보내면 지운다. 확인 전에는 다시 내려 주므로 앱이 id로 중복을 거른다. 계약 번호 1(옛 앱)은 내려 주면 바로 지운다. 차단한 사람 것·만료(7일)된 것은 언제나 지운다. 푸시(FCM) 없음 | 5-4, ADR-0014 |
| `POST /api/v1/reports` | `{"target":"<친구 공개 ID>" 또는 "requestId":"<받은 요청 ID>","reason":"POKE\|NICKNAME\|HARASSMENT\|OTHER","text":"신고할 잔소리(선택, 40자)"}` → 204. 친구·내가 차단한 사람·나에게 요청을 보낸 사람만 신고할 수 있다(아니면 404). 하루 10번 | ADR-0014 |
| `GET /api/v1/peers` | 또래 비교 `{"groups":[{"ageBand","occupation","n","p50","p75"}],"computedAtMs"}`. **그룹 집계만**, 10명 미만 그룹은 없음. 공유 동의한 사람만 받는다. 앱은 하루 1번 올리기 때 같이 받는다 | 5-5 |
| `PUT /api/v1/sharing` (6단계) | `consentVersion` 1 또는 2. **2 = 연속 일수·지금 시청 중 공유에 동의**(ADR-0012). 낮은 버전으로 되돌리지 않는다 | 6 |
| `PUT /api/v1/standing` (6단계) | `streakDays`(목표 이하 연속 일수, 0~3650 정수, 없으면 0)를 더 받는다. 계산은 앱(`core:insights.GoalStreak`) | 6 |
| `GET /api/v1/friends` (6단계) | 친구마다 `streakDays`와 `watching`(최근 40초 안 시청 신호가 있으면 true, ADR-0015)을 더 준다 | 6 |
| `PUT /api/v1/presence` | `{"watching":true}` 또는 `{"watching":false}`, 선택으로 `"ack":["받은 잔소리 id"]`. 숏폼을 보는 동안 앱이 15초마다 `true`(ADR-0015), 멈추면 `false` 한 번. 서버는 마지막 신호 시각만 남긴다(`false`면 지움). 동의 버전 2 미만이면 403. 응답 `{"pokes":[…]}`은 `GET /pokes`와 같은 모양이고 **내려 주면 지운다**(받는 앱이 바로 알림을 띄운다) | 6 |
| `POST /api/v1/pokes` (6단계) | 받는 친구가 지금 시청 중(최근 40초 안 신호)이 아니면 `409 not_watching` | 6 |
| `GET /invite/{코드}` | **로그인 없음.** 초대 QR이 여는 안내 페이지(코드 모양만 본다. 앱이 열리면 확인 창 → 연결 요청). 안드로이드는 `intent://invite/{코드}#Intent;scheme=sareureu;package=com.capstone.pause;S.browser_fallback_url=<APK>;end`로 앱을 열고, 앱이 없으면 설치 파일(`wrangler.toml`의 `APK_URL`, https만)로 간다. 틀린 코드는 404 | 6 |

또래 집계(5-5, 5-C): 이미 있는 하루 한 번 cron이 동의한 사용자 중 연령대·신분이 있고 **최근 14일에 유효한 날(기록된 비율을 모르거나 80% 이상)이 7일 이상**인 사람마다 하루 평균을 내고, 그룹마다 사람 수·중앙값·상위 25%만 `peer_stat`에 넣는다. 개인별 평균은 계산 중에만 쓰고 저장하지 않는다.
- 10명 미만이 된 그룹은 바로 지운다.
- 한 그룹의 중앙값이 하루 사이 30%(적어도 15분) 넘게 바뀌면 그날은 갱신하지 않는다(가짜 계정 몇 개나 잘못된 업로드가 값을 한 번에 흔들지 못하게). 예전 값이 3일 넘게 묵었으면 실제 변화로 보고 받아들인다.
- 읽는 양: cron은 표 전체가 아니라 최근 14일 창만 읽는다(`daily_watch_by_date` 인덱스). 사용자 1명당 하루 약 30행(14일 × 인덱스·본문 + 동의 행)이라 D1 무료 읽기 한도(하루 500만 행)의 10%를 쓰는 지점이 약 1만 6천 명, 한도 전체가 약 16만 명이다. 그보다 훨씬 먼저 시청 신호와 친구 목록 새로고침이 한도를 채우므로(아래 "무료 한도") 지금은 계산 방식을 바꾸지 않았다. 사람별 평균을 저장하면 "바뀐 날만" 다시 계산할 수 있지만 개인별 값을 저장하지 않는다는 원칙과 부딪힌다.
- 앱은 하루 한 번 업로드 시각을 사용자마다 무작위로 흩는다(`UploadJitter`).

6단계(ADR-0012, ADR-0015): `0006_streak_presence.sql`(`standing.streak_days`, `presence` 표). 매일 cron이 40초 넘은 `presence` 행도 지운다. 시청 신호는 15초마다라 시청 1시간당 사용자 1명 240번 D1 쓰기다(위 "무료 한도").

ADR-0014: `0007_friend_requests.sql`(`invite`를 해시 칸으로 다시 만들고, `friend_request`·`rate_event`·`idempotency`·`report` 추가, `poke_sent` 삭제).

마이그레이션: `0002_sharing_profile.sql`(닉네임·연령대·신분·공개 ID 칸, `standing` 표), `0003_friends.sql`(`invite`·`friendship`·`block`), `0004_pokes.sql`(`poke`, 하루 한도용 `poke_sent`), `0005_peer_stats.sql`(`peer_stat`, 그룹 집계). 동의 행을 지우면 모두 함께 지워진다. 매일 cron이 시간이 지난 초대 코드·잔소리를 지운다.

## 무료 한도 (ADR-0015)

Cloudflare 무료 요금제 한도(2026-11-23 공식 문서 `workers/platform/limits`, `d1/platform/pricing`): **Workers 요청 하루 10만 번**, **D1 읽기 하루 500만 행**, **D1 쓰기 하루 10만 행**(모두 UTC 0시에 초기화). 넘으면 Workers는 오류 1027로, D1은 질의 실패로 요청을 거부한다. 유료 요금제로 올리지 않는다.

사용자 한 명이 하루에 쓰는 양(가정: 숏폼 1시간 시청, 친구 탭 10분, 친구 10명. 읽기 행 수는 질의 모양에서 어림한 값이고 실제 측정값이 아니다):

| | 요청 | D1 쓰기 행 | D1 읽기 행 |
|---|---|---|---|
| 시청 신호(15초마다, 1시간) | 240 | 240 | 약 720 |
| 친구 탭 새로고침(15초마다, 10분) | 40 | 0 | 약 1,800 |
| 하루 합계·랭킹 숫자·또래 통계 받기 | 약 5 | 약 5 | 약 30 |
| **합계 (지금, 15초)** | **약 285** | **약 245** | **약 2,550** |
| 예전(신호 2초, 새로고침 3초) | 약 2,005 | 약 1,805 | 약 14,400 |

무료로 감당되는 사용자 수(위 가정으로 모두가 매일 쓸 때):

| 한도 | 지금(15초) | 예전(2초·3초) |
|---|---|---|
| Workers 요청 10만 | **약 350명** | 약 50명 |
| D1 쓰기 10만 행 | 약 400명 | 약 55명 |
| D1 읽기 500만 행 | 약 1,960명 | 약 350명 |

- 가장 먼저 차는 것은 **요청 수**다. 숏폼을 보는 시간이 절반이면 사용자 수는 약 두 배가 된다.
- 신호 한 번은 D1 요청 한 번(묶음 하나)이다. 무료 요금제의 "Worker 호출 한 번에 D1 질의 50번" 한도와는 거리가 멀다.
- 한도를 다 쓰면 앱은 저장해 둔 친구 목록으로 돌아가고, 주기 요청 간격을 실패할 때마다 두 배로 늘려 5분에서 멈춘다(`PollBackoff`).
- 웹소켓(Durable Objects)으로 바꾸는 방법은 ADR-0015에 장단점만 적어 두었다.

## 친구 연결 보안과 한도 (ADR-0014)

- **숫자는 `src/limits.mjs` 한 파일**에 있다(앱 쪽 짝은 `core:social`의 `SocialLimits`). 이유는 ADR-0014의 표.
- **한도는 원자적으로 센다.** `rate_event` 표에 "누가 무엇을 언제"만 남기고, `INSERT … SELECT … WHERE (개수 < 한도)` 한 문장으로 세고 넣는다(`src/ratelimit.mjs`). 코드 쓰기, 요청 수락(친구 50명), 잔소리 하루 한도도 한 문장 또는 한 `batch()`(트랜잭션)라서 두 요청이 동시에 와도 넘지 않는다. 한도를 넘으면 `429`와 `Retry-After`(초).
- **계약 번호**: 앱은 `X-App-Version: 2`를 보낸다. 헤더가 없으면 1(ADR-0014 이전 앱)로 본다. 서버의 최소 번호(`limits.minAppVersion`, 또는 `wrangler.toml`의 `MIN_APP_VERSION`)보다 낮으면 `426 upgrade_required`. 지금 최소는 1이라 옛 앱도 친구 목록·시청 신호·잔소리 받기는 그대로 된다(코드로 연결만 426).
- **폰 시계**: `measuredAtMs`가 서버 시각보다 5분까지 앞서도 받는다. 더 앞서면 `400 clock_skew`. 동의 직후 폰 시계가 조금 늦어 거절되던 것도 같은 5분만큼 봐준다.
- **버려진 계정**: 매일 cron이 60일 동안 랭킹 숫자·하루 합계 업로드가 없는 계정의 동의 행을 지운다(딸린 기록도 함께).
- **IP 기준 제한**: `wrangler.toml`에 Rate Limiting 바인딩 `IP_LIMITER`가 있으면 동의·연결 요청·잔소리 보내기·신고 경로에 IP마다 60초에 20번(대략적, 지역마다 따로 셈). 넘으면 `429 too_many_requests`. 바인딩이 없으면 건너뛴다. IP는 저장하지 않는다. WAF 규칙은 `workers.dev` 주소에 걸 수 없어 쓰지 않는다.
- **글 검사**: 금지 표현 목록 원본은 `docs/05_AI_SPEC.md` 7절. 고치면 `node scripts/bundle-banned-words.mjs`로 `src/banned-words.mjs`를 다시 만든다(시험이 어긋나면 알려 준다).
- **로그**: 서버 코드는 `console`에 아무것도 찍지 않는다(시험으로 확인). 닉네임·잔소리·토큰·코드가 Cloudflare 로그에 남지 않는다.

### 신고 보기 (운영자)

Cloudflare 대시보드 › D1 › `sareureu-db` › Console에서:

```sql
-- 최근 신고(신고당한 사람의 닉네임, 사유, 문장)
SELECT r.created_at_ms, c.nickname AS target, r.reason, r.text
FROM report r JOIN sharing_consent c ON c.user_id = r.target ORDER BY r.created_at_ms DESC LIMIT 50;
-- 잔소리가 멈춘 계정(서로 다른 3명 이상에게 신고됨)
SELECT c.nickname, COUNT(DISTINCT r.reporter) AS reporters
FROM report r JOIN sharing_consent c ON c.user_id = r.target GROUP BY r.target HAVING reporters >= 3;
-- 잘못된 신고라 풀 때: 그 계정에 대한 신고를 지운다
-- DELETE FROM report WHERE target = (SELECT user_id FROM sharing_consent WHERE nickname = '…');
```

## 테스트

`server`에서 Node.js 24로 `node --test`를 실행한다. Worker 핸들러, 마이그레이션, 계정 격리, 새벽 4시 경계, 중복·오래된 업로드, 동의 철회와 보존 기간을 로컬 SQLite에서 확인한다.
