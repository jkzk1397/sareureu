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
| `POST /api/v1/invites` | 내 초대 코드(6자, 헷갈리는 글자 뺌) `{"code","expiresAtMs"}`. 24시간, 한 번만, 새로 만들면 이전 코드는 없어진다 | 5-3 |
| `POST /api/v1/invites/{code}/accept` | 코드로 연결(코드를 만든 사람 + 입력한 사람 = 양쪽 동의). 404 없음·만료, 409 `own_invite`·`already_friends`·`unavailable`(차단, 누가 했는지 알리지 않음)·`too_many_friends`(50명) | 5-3 |
| `GET /api/v1/friends` | `{"friends":[{"id","nickname","reductionRate","coverage","validDays"}],"blocked":[{"id","nickname"}]}`. `id`는 공개 ID(Firebase uid 아님). 순위·꼴찌 숨기기는 앱이 `Ranking.build`로 | 5-3 |
| `DELETE /api/v1/friends/{id}` | 연결 끊기(양쪽 목록에서 빠짐) | 5-3 |
| `PUT/DELETE /api/v1/blocks/{id}` | 차단(연결도 끊음)·차단 풀기(연결은 되살리지 않음) | 5-3 |
| `POST /api/v1/pokes` | `{"to":"<친구 공개 ID>","text":"40자까지"}` 잔소리 맡기기. 친구에게만(차단하면 연결이 끊겨 403), 하루 10개(429). 금지 표현 검사는 앱이 보내기 전에 | 5-4 |
| `GET /api/v1/pokes` | 나에게 온 잔소리 `{"pokes":[{"id","fromId","fromNickname","text","createdAtMs","expiresAtMs"}]}`(20개까지). **내려 주면 서버에서 지운다.** 차단한 사람 것·만료(7일)된 것도 지운다. 앱이 화면에 있을 때(15분에 한 번까지)와 WorkManager(12시간마다)만 부른다. 푸시(FCM) 없음 | 5-4 |
| `GET /api/v1/peers` | 또래 비교 `{"groups":[{"ageBand","occupation","n","p50","p75"}],"computedAtMs"}`. **그룹 집계만**, 10명 미만 그룹은 없음. 공유 동의한 사람만 받는다. 앱은 하루 1번 올리기 때 같이 받는다 | 5-5 |

또래 집계(5-5): 이미 있는 하루 한 번 cron이 동의한 사용자 중 연령대·신분이 있고 최근 7일에 3일 이상 기록이 있는 사람마다 하루 평균을 내고, 그룹마다 사람 수·중앙값·상위 25%만 `peer_stat`에 통째로 바꿔 넣는다. 개인별 평균은 계산 중에만 쓰고 저장하지 않는다.

마이그레이션: `0002_sharing_profile.sql`(닉네임·연령대·신분·공개 ID 칸, `standing` 표), `0003_friends.sql`(`invite`·`friendship`·`block`), `0004_pokes.sql`(`poke`, 하루 한도용 `poke_sent`), `0005_peer_stats.sql`(`peer_stat`, 그룹 집계). 동의 행을 지우면 모두 함께 지워진다. 매일 cron이 시간이 지난 초대 코드·잔소리를 지운다.

## 테스트

`server`에서 Node.js 24로 `node --test`를 실행한다. Worker 핸들러, 마이그레이션, 계정 격리, 새벽 4시 경계, 중복·오래된 업로드, 동의 철회와 보존 기간을 로컬 SQLite에서 확인한다.
