# AGENTS.md — 이 저장소에서 일하는 모든 AI 코딩 도구의 공통 규칙

> 규칙의 원본은 이 파일 하나다. `CLAUDE.md`, `GEMINI.md`, `.github/copilot-instructions.md`, `.cursor/rules/`는 이 파일을 가리키기만 한다.
> 규칙을 바꿀 때는 이 파일만 고치고 PR에 `rules` 라벨을 단다. 짧게 유지한다(일부 도구는 32KB까지만 읽는다). 자세한 내용은 `docs/`에 둔다.

## 0. 이 앱은 — 사르르 (Sareureu)
숏폼(유튜브 쇼츠·인스타 릴스·틱톡)을 **막는 앱이 아니라, 볼 때 말 걸어 주는 AI 친구**다.
내가 **언제(맥락)·어디서 넘어와(경로)·얼마나** 숏폼에 빠지는지 자동으로 분석하고, 그 패턴에 맞는 순간에 캐릭터·메신저형 알림·채팅으로 말을 건다.
떠 있는 캐릭터, '5분만 더' 뒤에 오는 메신저형 알림, 코치 채팅은 **모두 같은 AI 친구 르르(각설탕 캐릭터, 코드 이름 `Buddy`)** 다. 르르는 오늘 본 시간만큼 조금씩 녹고, 새벽 4시에 새 각설탕이 된다(06 문서 8절).
**코드에는 브랜드 이름을 쓰지 않는다.** 클래스·플러그인 접두사는 `Pause`(패키지 `com.capstone.pause`), 캐릭터는 `Buddy`. '사르르'·'르르'는 `R.string.app_name`·`R.string.buddy_name`과 문구 팩에만 있다. 이름이 또 바뀌어도 문자열·아이콘·그림만 고치면 된다.
핵심 지표: **기준선 대비 감소율(reductionRate)**, **알림 이후 스스로 멈춘 비율(stopRate)**, **위험 맥락(자기 전·한밤중) 시청 시간**.

사람에게 쓰는 모든 말(보고, 질문, PR 설명, 커밋 메시지)은 한국어로 쓴다.

## 1. 작업 전에 읽을 것 (이 순서로)
1. 이 파일
2. 작업할 모듈의 `<모듈>/AGENTS.md` — 담당 역할, 공개 API, 의존 가능 모듈, 금지 사항
3. 이슈 본문, `docs/PROGRESS.md`
4. 필요한 문서만: 기능 `docs/01_PRD.md` · 구조 `docs/03_ARCHITECTURE.md` · 데이터 `docs/04_DATA_PRIVACY.md` · AI `docs/05_AI_SPEC.md` · 화면 `docs/06_DESIGN_SYSTEM.md` + `docs/design/mockups/*.png`
5. 결정 기록 `docs/decisions/ADR-*.md` — **이미 내린 결정을 다시 뒤집지 않는다.** 바꾸려면 새 ADR을 쓰고 리드 승인을 받는다.

## 2. 명령어
| 목적 | 명령 |
|---|---|
| 디버그 빌드 | `./gradlew assembleDebug` |
| PR 전 필수 검사 | `./gradlew spotlessApply` 다음 `./gradlew check` |
| 한 모듈만 테스트 | `./gradlew :core:insights:test` (모듈 경로만 바꿔서) |
| AI 평가 (모의) | `./gradlew :core:ai-engine:aiEval -Pprovider=mock` |
| AI 평가 (실제) | `GEMINI_API_KEY=... ./gradlew :core:ai-engine:aiEval -Pprovider=gemini` (키는 커밋 금지) |
| 시연 데이터 | 앱 > 설정 > 시연 모드 > "2주치 가상 기록 넣기" |
| 폰·에뮬레이터에 설치 | `./gradlew installDebug` (Android Studio ▶도 같음) |
| 윈도우 PowerShell에서 | `./gradlew` 대신 `.\gradlew.bat` (Claude Code는 Git Bash라 `./gradlew` 그대로) |

## 3. 황금 규칙 12
1. **범위 밖 파일을 고치지 않는다.** 이슈에 적힌 모듈·파일만. 다른 곳이 필요하면 멈추고 PR 설명의 "범위 밖 요청"에 적는다.
2. **계약은 계약 PR로만 바꾼다.** 계약 = `core:model`의 타입, 각 모듈의 public interface, Room 스키마, 프롬프트 출력 스키마, 내비 경로, 디자인 토큰, 9절 용어집. 브랜치 `contract/…`, 라벨 `contract`, 담당 역할 승인 필수.
3. **숫자는 코드가 센다. AI는 문장만 쓴다.** 통계·순위·감소율은 `core:insights`가 계산한다. LLM 문장에 근거 없는 숫자가 들어가면 검증기가 막는다.
4. **AI는 행동을 정하지 않는다.** 다른 앱을 닫거나 누르거나 조작하지 않는다(Google Play 접근성 API 정책). 언제 개입할지는 고정 규칙 엔진(`core:intervention`)이 정하고, AI는 미리 만들어 둔 문구 후보만 준다. `core:intervention`은 `core:ai`에 의존할 수 없다.
5. **화면의 글자·영상 제목·메시지 내용은 저장하지 않는다.** 접근성은 "지금 숏폼 화면인가"(참/거짓)와 스크롤·클릭 이벤트 **개수**만 쓴다.
6. **다른 앱이나 브랜드를 흉내 내지 않는다.** 카카오톡 등 타사의 이름·로고·화면을 쓰지 않는다. 메신저 느낌은 `NotificationCompat.MessagingStyle`과 우리 캐릭터로 낸다.
7. **의존성 추가·버전 변경 금지.** `gradle/libs.versions.toml`은 리드만 고친다. 필요하면 이슈로 요청한다.
8. **하드코딩 금지.** 화면 문구는 그 모듈의 `res/values/strings.xml`(한국어), 색·글꼴·간격·모양은 `core:designsystem` 토큰, 현재 시각은 주입한 `Clock`, 앱 이름은 `R.string.app_name`, 캐릭터 이름은 `R.string.buddy_name`.
9. **테스트를 지우거나 끄지 않는다.** lint baseline, Spotless 규칙, `@Suppress`로 덮지 않는다.
10. **시연 모드는 언제나 돌아가야 한다.** 권한이 하나도 없어도 `DemoEventSource`와 가상 데이터로 모든 화면과 개입 흐름이 보여야 한다.
11. **새 권한은 ADR 없이 추가하지 않는다.** 권한마다 사용자 고지 문구와 거절했을 때의 대체 흐름이 있어야 한다.
12. **비밀값을 커밋하지 않는다.** API 키, `google-services.json`, keystore, `local.properties`.

## 4. 모듈과 담당 역할
역할: **R1** 리드·기반 / **R2** 감지·데이터 / **R3** 실시간 개입 / **R4** AI·분석 / **R5** 디자인·화면 / **R6** 소셜·백엔드. 실제 사람은 `.github/CODEOWNERS`. 한 사람이 여러 역할을 맡아도 된다.

| 모듈 | 역할 | 하는 일 | 의존 가능 |
|---|---|---|---|
| `app` | R1 | Application, MainActivity, 내비 그래프, 하단 탭 | 모든 feature, core |
| `build-logic` | R1 | Gradle 컨벤션 플러그인 | — |
| `core:model` | R1 | 순수 Kotlin 도메인 타입 (계약) | 없음 |
| `core:common` | R1 | Clock, 디스패처, Result, 로그 인터페이스 (순수 JVM) | model |
| `core:testing` | R1 | 테스트용 Fake, 고정 시나리오 | 모든 core |
| `core:database` | R2 | Room DB, DAO, 마이그레이션 | model, common |
| `core:data` | R2 | 저장소 인터페이스와 구현, DataStore(설정·프로필), 기본 문구 팩·전략 자산, `DemoController` 인터페이스 | database, insights, model, common |
| `core:sensing` | R2 | 사용 기록 수집, 세션 만들기, 맥락·경로 분류, 접근성 감지, 시연 이벤트 | data, model, common |
| `core:insights` | R4 | 순수 계산: 집계, 맥락·경로 분석, 위험 시간대, 기준선·감소율, 유형, 또래 비교 | model, common (안드로이드 의존 금지) |
| `core:intervention` | R3 | 포그라운드 서비스, 개입 규칙 엔진, 플로팅 캐릭터·카드 오버레이, 알림·예약 알림, `DemoController` 구현·`NudgePreviewActivity` | sensing, data, designsystem, model, common (**ai 금지**) |
| `core:ai-engine` | R4 | **순수 JVM**: 프롬프트 레지스트리(리소스), 출력 스키마, 검증기, 안전 필터, 모의·REST 클라이언트, 코치 도구 루프, 평가 하네스 | insights, model, common |
| `core:ai` | R4 | 안드로이드: Firebase·기기 안 LlmClient, AiRouter, DI, 감사 로그 저장, 리포트·문구 팩 생성기 | ai-engine, data, insights, model, common |
| `core:social` | R6 | 친구, 잔소리, 랭킹·리그, Firebase 연동 | data, insights, model, common |
| `core:designsystem` | R5 | 테마(라이트·다크), 토큰, 컴포넌트, 차트, 캐릭터 | model, common |
| `feature:onboarding` | R3 | 프로필·하루 리듬, 권한 고지·요청, AI 동의 | designsystem, data, sensing, intervention |
| `feature:today` | R5 | 오늘 요약, 실시간 상태, 캐릭터 한마디, 오늘의 맥락 | designsystem, data |
| `feature:report` | R5 | 시간대 범주, 진입·이탈 흐름, 히트맵, 또래 비교, AI 주간 리포트, 유형 카드 | designsystem, data, ai |
| `feature:coach` | R4 | AI 친구 채팅(근거형), 르르 메시지 스레드 | designsystem, data, ai |
| `feature:friends` | R6 | 친구, 랭킹, 주간 리그, 잔소리, 함께 챌린지 | designsystem, data, social, ai |
| `feature:settings` | R1 | 알림·임계값·추적 앱, 감지 방식, AI 선택·감사 로그, 개인정보, 테마, 시연 | designsystem, data, intervention, ai, social |

- feature 모듈끼리는 서로 의존하지 않는다. 다른 모듈의 `internal`은 쓰지 않는다.
- **순수 JVM 모듈**(안드로이드 의존 금지, `pause.jvm.library`): `core:model`, `core:common`, `core:insights`, `core:ai-engine`. 에뮬레이터 없이 빠르게 테스트된다.
- 각 모듈 루트에 `AGENTS.md`(10줄 이내: 역할, 하는 일, 공개 API, 의존 가능, 금지)를 둔다.

## 5. 충돌이 잦은 파일 (핫스팟)
| 파일 | 규칙 |
|---|---|
| `settings.gradle.kts` | R1만. 모듈 추가는 그것만 하는 PR로 |
| `gradle/libs.versions.toml` | R1만. 알파벳순, 한 줄에 한 항목 |
| `app/src/main/AndroidManifest.xml` | R1만. 권한·서비스·리시버는 **각 모듈의 Manifest**에 두고 병합에 맡긴다 |
| `strings.xml` | 모듈마다 따로. 키 접두사 = 모듈 이름(`today_`, `coach_`). 파일 끝에 추가만 |
| 내비 경로 `app/…/navigation/` | R1만. Navigation 3: 경로 키(`@Serializable` + `NavKey`)는 각 feature의 `navigation/` 패키지에, feature는 entry provider에 붙이는 확장 함수(`xxxEntries()`) 하나만 공개. 백 스택은 `app`이 가진다 |
| Hilt 모듈 | 각 모듈의 `di/` 패키지 안에만 |
| Room 스키마 | R2만. 버전을 올리면 마이그레이션 + 마이그레이션 테스트 + `schemas/` JSON을 같은 PR에 |
| 프롬프트 `core/ai-engine/src/main/resources/prompts/` | 기존 파일은 고치지 않는다. 새 버전 파일(`weekly_report_v2.md`)을 추가하고 `PromptRegistry`의 현재 버전만 바꾼다 |
| 디자인 토큰 | R5만 |
| `docs/PROGRESS.md` | 자기 줄의 체크박스만 바꾼다 |

## 6. 코드 규칙
- 구조: Compose 화면 → ViewModel(`StateFlow<XxxUiState>`) → 저장소 인터페이스(`core:data`) → 데이터 소스. 단방향 흐름.
- 화면은 `XxxRoute(viewModel)`와 상태 없는 `XxxScreen(uiState, onAction)`로 나눈다. 모든 Screen에 라이트·다크 `@Preview`.
- Composable 안에 비즈니스 로직·계산을 두지 않는다. 계산은 `core:insights`의 순수 함수로 만들고 단위 테스트한다.
- 코루틴: 디스패처는 주입(`@Dispatcher(IO)`), `GlobalScope` 금지, 앱 수명 작업은 `@ApplicationScope`.
- 시간: `java.time` + 주입한 `java.time.Clock`. `System.currentTimeMillis()` 직접 호출 금지(감지 모듈의 이벤트 타임스탬프는 예외).
- 이름: 9절 용어집의 영어 이름만 쓴다. 새 개념은 용어집에 먼저 추가하는 계약 PR.
- 리소스 이름 접두사 = 모듈 이름(`today_ic_flame`, `coach_title`).
- 접근성: 아이콘 버튼 `contentDescription`, 터치 영역 48dp 이상, 색만으로 의미를 전하지 않는다(아이콘·글자 병행).
- 로그: `core:common`의 `AppLog`만. 사용 기록·채팅 내용·프로필을 로그에 남기지 않는다.
- `!!` 금지(테스트 제외). 공개 함수에는 KDoc 한 줄.
- 캐릭터는 `core:designsystem`의 `BuddyAvatar(expression, size, melt)` 하나로만 그린다. 몸은 그림 한 장(`designsystem_buddy_body`), 얼굴은 코드(06 문서 8절). 표정별 그림 파일을 새로 만들지 않는다.

## 7. AI 기능 규칙 (자세히는 docs/05_AI_SPEC.md)
- LLM 호출은 `LlmClient` 인터페이스(`core:ai-engine`)로만. 구현: 클라우드(Firebase AI Logic, `core:ai`), 기기 내(Gemini Nano, 지원 기기만, `core:ai`), 모의(`MockLlmClient`, 규칙 기반, `core:ai-engine`). 앱 코드에서 REST 클라이언트(평가 전용)를 쓰지 않는다.
- 프롬프트는 버전 파일로만 둔다. Kotlin 코드에 프롬프트 문자열을 쓰지 않는다.
- 모든 출력은 JSON 스키마 파싱 → `XxxValidator` 통과 후에만 화면에 나온다. 두 번 실패하면 모의 결과로 대신하고 감사 로그에 남긴다.
- **앱이 화면에 있을 때만 LLM을 부른다.** 캐릭터·메신저 알림 문구는 리포트를 만들 때 "문구 팩"으로 미리 생성해 저장하고, 개입 순간에는 규칙이 그중 하나를 고른다.
- 위기 표현(`SafetyFilter`)이 보이면 AI 호출을 멈추고 도움 카드(자살예방 109, 정신건강위기 1577-0199)를 띄운다.
- 금지 표현(`BannedWords`, 목록 원본은 `docs/05_AI_SPEC.md` 7절)과 근거 없는 숫자는 검증기가 막는다. 훈계·수치심·외모·성적 비하 문구 금지. 장난스러운 말투도 놀림이 아니라 친구의 장난까지만.
- 클라우드로 보내는 것: `core:insights`가 계산한 요약, 프로필의 연령대·신분·관심사, 앱 **종류**(메신저·공부 등). 원시 이벤트, 설치 앱 목록, 연락처, 정확한 위치, 친구 이름은 보내지 않는다.
- 모든 호출을 `AiCallLog`에 남긴다(기능, 프롬프트 버전, 제공자, 지연, 검증 결과).

## 8. Git 흐름
- 브랜치: `feat/<모듈>-<설명>`, `fix/…`, `contract/…`, `docs/…`, `chore/…`
- 커밋: Conventional Commits, 한국어 가능 — `feat(report): 진입·이탈 흐름 차트 추가`
- PR: 이슈 하나 = PR 하나, 사람이 쓴 변경 400줄 이하 권장, `main` 직접 push 금지, squash merge, CI 통과 필수, 템플릿 체크리스트를 채운다.
- 시작 전 `git pull --rebase origin main`, PR 올리기 전 다시 rebase. 충돌은 먼저 머지된 쪽을 기준으로 푼다.
- 같은 파일을 두 사람이 동시에 고쳐야 하면 이슈에서 순서를 정한다.

## 9. 용어집 (화면 말 ↔ 코드 이름)
| 화면 말 | 코드 |
|---|---|
| AI 친구(캐릭터) | `Buddy` |
| 숏폼 세션 (앱을 넘나들어도 이어지면 하나) | `ShortformSession`, 앱별 구간 `AppSegment` |
| 맥락(시간대 범주) | `ContextSlot` (WAKE_UP, COMMUTE, CLASS_WORK, MEAL, BREAK, PRE_SLEEP, LATE_NIGHT, OTHER — 이 순서가 색·쌓는 순서) |
| 진입 경로 / 이탈 경로 | `EntryPoint` / `ExitPoint` |
| 화면 위 카드 | `NudgeCard` |
| 5분만 더 / 단계별 어려움 | `Extension` / `FrictionLevel` |
| 르르 메시지(캐릭터가 보내는 메신저형 알림) | `BuddyMessage` |
| 잔소리(친구가 미리 맡겨 두는 메시지) | `FriendPoke` |
| 르르 표정 / 녹은 정도 | `BuddyExpression` (NEUTRAL, WATCHING, CALLING, WORRIED, CHEER, SLEEPY) / `MeltStage` (NONE, SOFT, PUDDLE, MELTED = 0~3) |
| 예약 알림 / 위험 시간대 미리 알림 | `ScheduledReminder` / `RiskWindow`, `PredictiveNudge` |
| 한 탭 체크인 (어땠어?) | `CheckIn` (`Mood.GOOD / MEH / BAD`) |
| 넘김·클릭 통계 | `InteractionStats` |
| 대처 전략 카드 / 만약~하면 계획 | `StrategyCard` / `IfThenPlan` |
| 주간 리포트 / 숏폼 유형 | `WeeklyReport` / `ViewerType` |
| 관찰(코드가 센 것) / 추측(AI) | `Observation` / `Hypothesis` |
| 코치 채팅 | `CoachChat` |
| 기준선 / 감소율 | `Baseline` / `reductionRate` |
| 친구 랭킹 / 주간 리그 | `FriendRanking` / `WeeklyLeague` |
| 또래 비교 | `PeerBaseline` |
| 기록된 비율(감시 범위) | `coverage` |
| 시연 모드 | `DemoMode` |
| AI 감사 로그 | `AiCallLog` |

## 10. AI 에이전트 작업 절차
**시작** 1) `git pull --rebase origin main` 2) 1절 문서 읽기 3) 바꿀 파일 목록과 테스트 계획을 먼저 적는다 4) 그 파일만 고친다
**끝** 5) `./gradlew spotlessApply` → `./gradlew check` 6) `docs/PROGRESS.md` 체크 7) PR 설명에 **인수인계 메모**: 한 일 / 안 한 일 / 범위 밖 요청 / 확인 방법(화면 경로 또는 테스트 이름)
**막히면** 계약을 추측으로 바꾸지 않는다. 인터페이스 뒤에 Fake 구현 + `// TODO(#이슈번호): 이유`로 진행하고 이슈에 남긴다.
**질문 대신 가정** 사소한 결정은 합리적으로 정하고 PR에 "가정"으로 적는다. 계약·권한·개인정보에 닿는 결정만 멈춰서 묻는다.

## 11. 에이전트가 하지 말 것
- Gradle wrapper, AGP, Kotlin, `compileSdk`/`targetSdk`/`minSdk` 올리기
- 패키지·모듈 이름, `applicationId` 바꾸기
- 관련 없는 파일 포맷팅, import 정리, 파일 이동
- 생성 파일(`build/`, `*.iml`, `.idea/` 대부분) 커밋
- 다른 모듈의 공개 API 바꾸기, `internal` 우회
- 테스트 삭제·`@Ignore`, lint baseline 갱신
- 타사 로고·이름·UI 복제, 실존 인물 사진 사용
- 사용자 기록을 원격으로 보내는 코드를 동의 확인 없이 추가
