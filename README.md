# 사르르 (Sareureu)

숏폼(유튜브 쇼츠·인스타 릴스·틱톡)을 **막지 않고, 볼 때 말 걸어 주는 AI 친구** 앱. 캐릭터는 각설탕 르르 — 오늘 본 만큼 조금씩 녹고, 새벽 4시에 새 각설탕이 된다.
언제(맥락)·어디서 넘어와(경로)·얼마나 보는지 기기 안에서 세고, 그 패턴에 맞는 순간에 캐릭터·메신저형 알림·채팅으로 말을 건다.

- 규칙: [`AGENTS.md`](AGENTS.md) · 기능: [`docs/01_PRD.md`](docs/01_PRD.md) · 구조: [`docs/03_ARCHITECTURE.md`](docs/03_ARCHITECTURE.md) · 데이터: [`docs/04_DATA_PRIVACY.md`](docs/04_DATA_PRIVACY.md) · AI: [`docs/05_AI_SPEC.md`](docs/05_AI_SPEC.md) · 디자인: [`docs/06_DESIGN_SYSTEM.md`](docs/06_DESIGN_SYSTEM.md)
- **팀원 첫날 가이드(지금 상태·남은 일·설치·폴더 구성·PR 방법·Android Studio 없이 확인하기): [`docs/TEAM_SETUP.md`](docs/TEAM_SETUP.md)**
- 빌드 결과·가정·남은 일·사람이 할 확인: [`docs/BUILD_REPORT.md`](docs/BUILD_REPORT.md) · 진행: [`docs/PROGRESS.md`](docs/PROGRESS.md) · Play 제출 문안: [`docs/PLAY_SUBMISSION.md`](docs/PLAY_SUBMISSION.md)

## 빌드와 실행
| 목적 | 명령 (Git Bash / macOS·Linux) | Windows PowerShell |
|---|---|---|
| 디버그 빌드 | `./gradlew assembleDebug` | `.\gradlew.bat assembleDebug` |
| 폰·에뮬레이터에 설치 | `./gradlew installDebug` | `.\gradlew.bat installDebug` |
| PR 전 검사 | `./gradlew spotlessApply` → `./gradlew spotlessCheck check assembleDebug` | 같은 작업을 `.\gradlew.bat`로 |
| AI 평가(모의) | `./gradlew :core:ai-engine:aiEval -Pprovider=mock` | 같음 |
| AI 평가(실제 Gemini) | `GEMINI_API_KEY=... ./gradlew :core:ai-engine:aiEval -Pprovider=gemini` | `$env:GEMINI_API_KEY="..."; .\gradlew.bat ...` |

- 필요: Android Studio 최신 안정판(AGP 9.5, SDK Platform 37), JDK 21(Android Studio 내장 JBR도 됨), `ANDROID_HOME`. 버전은 [`docs/03_ARCHITECTURE.md`](docs/03_ARCHITECTURE.md) 6절 표.
- `local.properties`(커밋 안 함)에 `sdk.dir`이 없으면 Android Studio가 만들어 준다.
- AI 평가 보고서: `core/ai-engine/build/reports/ai-eval/report.md`·`report.csv`.
- CI(`.github/workflows/android-ci.yml`)가 PR마다 같은 검사를 돌리고 디버그 APK를 `app-debug` 아티팩트로 14일 보관한다(로컬 빌드 없이 폰에 깔아 보기).

## 시연 모드 (권한 없이 모든 화면)
1. 처음 실행 → 온보딩(연령 확인 → 개인정보 요약 → … → 권한은 "지금은 안 할래"로 넘겨도 됨) → 시작하기.
2. 하단 **설정** › **시연** › 시연 모드 켜기 → 지금 시각 기준 2주치 가상 기록과 가상 친구 3명이 들어간다. 패턴(밤형·출퇴근형·알림 도미노형)을 고르거나 "2주치 가상 기록 넣기"로 다시 넣을 수 있다.
3. 화면 위 캐릭터·카드까지 보려면 '다른 앱 위에 표시'를 허용한다(없으면 같은 카드가 앱 안 화면으로 뜬다). 알림 권한을 주면 메신저형 알림 헤드업도 보인다.

## 실제 감지
설정 › **감지 켜기** → 사용 기록 접근 고지 → 시스템 설정에서 허용 → 돌아오면 상태 알림과 함께 감지가 돈다. 유튜브·인스타그램·틱톡을 보면 맥락별 기준 시간(기본 15분, 한밤중 10분)에 카드가 뜬다. 설정 › 개발자 › 최근 세션에서 저장된 세션을 볼 수 있다.

## 클라우드 AI 붙이기 (선택)
없으면 앱은 규칙 기반 모의 AI로 모든 AI 화면을 돌린다(화면에 "모의 AI" 표시).
1. Firebase 프로젝트 → 안드로이드 앱 `com.capstone.pause` 등록 → `app/google-services.json`을 **로컬에만** 둔다(있으면 Gradle 플러그인이 자동 적용, 커밋 금지).
2. Firebase AI Logic에서 Gemini Developer API 켜기.
3. App Check: 디버그 빌드를 한 번 실행해 logcat의 디버그 토큰을 콘솔에 등록(커밋 금지). 릴리스는 Play Integrity.
4. 앱 설정 › AI 방식 › 클라우드 → 국외 이전 고지 확인 → 동의. 설정 › AI가 한 일에서 호출 기록을 본다.

## 시연 수용 테스트 (PRD 11절, 5분)
사람이 따라 하는 체크리스트. 결과는 [`docs/BUILD_REPORT.md`](docs/BUILD_REPORT.md) "시연 리허설" 절에 적는다.

| # | 할 일 | 보여야 하는 것 | 목업 |
|---|---|---|---|
| 1 | 설정 › 시연 모드 켜기 | "시연 데이터" 배지, 2주치 가상 기록, 가상 친구 3명 | — |
| 2 | 오늘 탭 | 하루 시계(위가 0시, 위 반이 밤, 맥락 색 꽃잎, 지금 위치 점, 위험 시간대 점선), 어제 대비, 르르 한마디, "밤 ○시부터가 제일 위험해" 카드, 받은 잔소리·내 계획 | `01_today_light.png`, `01_today_dark.png` |
| 3 | 오늘 › "시연: 숏폼 보기 시작" | 플로팅 르르(경과 링) → 약 15초 뒤 카드 1단계("유튜브 15분째야", "5분만 더 볼게" 따라 쓰기) → '5분만 더' → 메시지 알림 헤드업("르르 · 사르르" 또는 "민지 · 맡겨 둔 잔소리") + 카드 2단계(이유 칩 → 5초 → 문장 따라 쓰기) → '5분만 더' → 카드 3단계(10초 숨 고르기 + 더 긴 문장 따라 쓰기) → '그만 볼래' → 칭찬 카드 | `02_overlay_card.png`, `03_message_alert.png`, `04_friction.png` |
| 4 | 리포트 탭 | 하루 평균·기준선 대비·알림 뒤 멈춤, 유형 카드(밤 산책형), 일주일 시계(시계/표), 맥락별 합계, 진입→앱→이탈 흐름도, 세션 길이, 또래 비교(시연용 가상 데이터), 관찰(코드)과 'AI 주간 분석 · 추측' 분리, 가설 3개·근거 칩 → "반쯤" → 전략 고르기 → 계획으로 저장 | `05_report.png`, `06_ai_weekly.png` |
| 5 | 코치 탭 › "왜 자기 전에 못 끊을까?" | 기록을 조회한 답 + 근거 칩 + 신고, 추천 질문 | `07_coach.png` |
| 6 | 친구 탭 | 감소율 랭킹(상위 3 + 나, 측정 중 표시), 주간 리그 '곧 열려요', 잔소리 맡기기 → "AI 초안" → 고르기 → 맡기기, 함께 챌린지 | `08_friends.png` |
| 7 | 설정 › AI가 한 일 | 호출마다 시각·기능·제공자·검증 결과·지연, 펼치면 입력 해시·통과본 | — |

## 모듈
`app` · `build-logic` · `core:{model, common, database, data, sensing, insights, intervention, ai-engine, ai, social, designsystem}` · `feature:{onboarding, today, report, coach, friends, settings}`. 각 모듈의 역할·공개 API·금지 사항은 그 폴더의 `AGENTS.md`.

## 라이선스
글꼴 Pretendard(서브셋, 내부 이름 "Pause Sans") — SIL Open Font License 1.1, [`licenses/Pretendard-OFL.txt`](licenses/Pretendard-OFL.txt).
