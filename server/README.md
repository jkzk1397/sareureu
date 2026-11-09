# 일일 시청 합계 수집 Worker

안드로이드 앱이 기기에서 계산한 숏폼 시청 분과 기록률을 D1에 저장한다. 날짜는 앱과 같은 현지 시각 새벽 4시를 기준으로 하고, 서버는 수신·저장 시각을 UTC로 기록한다. 앱의 세션 시작·종료 시각, 앱 이름, 원시 이벤트는 보내지 않는다.

## Cloudflare 설정

1. `server` 폴더에서 `npx wrangler login`을 실행한다. 기존 D1이 있으면 `npx wrangler d1 list`로 확인하고, 없으면 `npx wrangler d1 create sareureu-db`로 만든다.
2. `wrangler.toml`에 실제 `database_name`, `database_id`, `FIREBASE_PROJECT_ID`를 입력한다. Firebase 프로젝트 ID는 안드로이드에서 로그인에 사용할 프로젝트와 같아야 한다.
3. `server` 폴더에서 `npx wrangler d1 migrations apply DB --remote`를 한 번 실행한다. 로컬 검증에는 `--local`을 쓴다.
4. Cloudflare의 `sareureu` Worker에서 **Settings → Builds → Root directory**를 `server`로 지정한다. **Build command**는 비워 두고 **Deploy command**는 `npx wrangler deploy`로 설정한다. 설정과 코드가 배포 대상 브랜치에 있으면 빌드를 다시 실행한다.
5. Worker의 `GET /health`가 `204`를 돌려주면 D1 바인딩과 테이블이 연결된 것이다.

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
- 현재 앱의 소셜 구현은 시연용이다. 실제 앱 연동에는 Firebase 로그인, 별도 랭킹 공유 동의 화면, 집계 전송 작업이 필요하다. 친구 관계 및 랭킹 조회는 이 수집 API와 별도다. 기존 랭킹은 개인 기준선 대비 감소율을 사용한다(ADR-0005).

## 테스트

`server`에서 Node.js 24로 `node --test`를 실행한다. Worker 핸들러, 마이그레이션, 계정 격리, 새벽 4시 경계, 중복·오래된 업로드, 동의 철회와 보존 기간을 로컬 SQLite에서 확인한다.
