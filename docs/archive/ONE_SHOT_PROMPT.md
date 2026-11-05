# 원샷 빌드 프롬프트 — 사르르 (Android)

> **사람이 읽는 부분**: 저장소 루트에서 AI 코딩 에이전트(Claude Code, Codex, Gemini CLI, Android Studio 에이전트 등)를 열고, 이 파일 전체를 첫 메시지로 붙여 넣거나 "ONE_SHOT_PROMPT.md를 읽고 그대로 실행해"라고 말한다. 한 사람(R1)만 실행한다. 세션이 끊기면 "ONE_SHOT_PROMPT.md와 docs/PROGRESS.md를 다시 읽고 다음 단계부터 이어서"라고 말한다.
> 시작 전에 사람이 할 일 3가지: ① 빌드 환경 — `KIT_GUIDE.md` '시작 순서' 0~1: Android Studio 최신 안정판(2026-11 기준 Rabbit 2 · 2026.2.2, AGP 9.5, SDK Platform 37)과 Git for Windows 설치, `JAVA_HOME`·`ANDROID_HOME` 설정, 저장소 clone. Gradle wrapper(`gradlew`, `gradlew.bat`, `gradle/wrapper/`)는 키트에 들어 있다. ② 키트 파일이 덮어써지지 않았는지 확인하고 커밋한다. ③ (선택) Firebase 프로젝트를 만들고 `app/google-services.json`을 **로컬에만** 둔다.

---

## 0. 설정값
```
APP_NAME_KO    = 사르르                # 화면에 보이는 이름. R.string.app_name 하나로만 쓴다
APP_NAME_EN    = Sareureu
BUDDY_NAME_KO  = 르르                  # 캐릭터 이름. R.string.buddy_name 하나로만 쓴다
TAGLINE        = 숏폼에 녹아든 시간, 말 걸어 주는 친구
NAMESPACE      = com.capstone.pause    # 코드 패키지. 앱 이름이 바뀌어도 유지
APPLICATION_ID = com.capstone.pause    # 출시 전 바꿀 수 있음
MIN_SDK        = 29
TARGET_SDK     = 36                    # 2026-08-31부터 Google Play 신규 앱·업데이트 최소값
COMPILE_SDK    = 37                    # Android 17. AGP 9.5가 지원하는 최댓값
CLOUD_AI       = auto                  # google-services.json이 있으면 Firebase AI Logic, 없으면 Mock
CLOUD_MODEL    = gemini-3.5-flash-lite # AiConfig 기본값(안정판, 종료 2027-07-21 이후, 무료 Spark 가능). Remote Config로 바꿀 수 있게
CLOUD_MODEL_HQ = gemini-3.8-flash      # 선택: 주간 분석만 더 좋은 모델로. 기본은 쓰지 않음
MIN_AGE        = 18                    # 만 18세 미만 가입 불가(ADR-0009)
SOCIAL_BACKEND = off                   # off = 가상 친구만(MVP) / firebase = P1
UI_LANGUAGE    = ko
```

## 1. 너의 역할과 목표
너는 이 저장소의 리드 안드로이드 엔지니어다. 목표는 **캡스톤 최종 시연에서 처음부터 끝까지 끊김 없이 돌아가는 앱**을 만드는 것이다. 우선순위는 ① 빌드가 된다 ② 권한 없이도 시연 모드로 모든 화면과 개입 흐름이 보인다 ③ 실기기에서 실제 감지·개입이 돈다 ④ AI 기능이 검증을 거쳐 돈다 ⑤ 다듬기. 기능을 넓히기보다 이 순서를 지킨다.

## 2. 먼저 읽을 것 (이 순서로, 읽기 전에는 코드를 쓰지 않는다)
1. `AGENTS.md` — 모든 규칙의 원본. 이 프롬프트와 충돌하면 AGENTS.md가 이긴다.
2. `docs/01_PRD.md` — 무엇을 만드나(특히 6절 기능 검토, 7절 흐름, 9절 지표, 11절 시연, 12절 수용 기준)
3. `docs/03_ARCHITECTURE.md` — 모듈, 흐름, 권한, 버전 표
4. `docs/04_DATA_PRIVACY.md` — 도메인 타입(계약), 저장·동의
5. `docs/05_AI_SPEC.md` — AI 계약, 숫자 정책, 검증, 안전, 평가
6. `docs/06_DESIGN_SYSTEM.md` + `docs/design/mockups/*.png` — 토큰, 하루 시계, **르르(8절)**, 화면. 브랜드·말투는 `docs/brand/BRAND.md`, 팀 시안은 `docs/design/reference/team_mockup.png`
7. `docs/decisions/ADR-*.md` — 확정된 결정
8. `docs/PROGRESS.md` — 어디까지 했나
9. 각 모듈 폴더의 `AGENTS.md` — 그 모듈의 경계

## 3. 바꿀 수 없는 결정 (요약, 자세히는 ADR)
1. **막지 않고 말 건다.** 다른 앱을 닫거나 조작하지 않는다. '그만 볼래'는 모든 단계에서 1탭 → 홈 화면 인텐트.
2. **숫자는 코드, 말은 AI.** 생성 문구는 자리표시자만, 채팅 숫자는 도구 결과와 일치해야 한다. 모든 AI 출력은 스키마 + 검증기 통과 후에만 표시.
3. **카톡 위장 금지.** 메신저 느낌은 우리 앱의 `MessagingStyle` 알림(르르 또는 친구가 맡긴 잔소리)으로.
4. **감지 소스 3종을 같은 인터페이스로**: 사용 기록(기본) / 접근성(선택 정밀 모드) / 시연.
5. **개입 엔진은 AI에 의존하지 않는다.** 문구는 미리 만든 문구 팩에서 고른다. LLM은 앱이 화면에 있을 때만 부른다.
6. **원시 기록은 기기 밖으로 나가지 않는다.** 클라우드에는 계산된 요약과 동의한 합계만.
7. **랭킹은 기준선 대비 감소율**, 기록률 80% 미만인 날 제외, 꼴찌 비공개.
8. **API 키를 앱에 넣지 않는다.** 클라우드 AI는 Firebase AI Logic + App Check, 없으면 Mock.
9. **르르는 몸 그림 한 장 + 코드 얼굴**(ADR-0008). 녹아도 아파하지 않는다. 코드에는 브랜드 이름 대신 `Pause`·`Buddy`.

## 4. 제품 한눈에
- 하단 탭 5개: **오늘**(하루 시계·보는 중 카드·위험 시간대·받은 잔소리·내 계획) / **리포트**(일주일 시계+표, 맥락별 합계, 진입·이탈 흐름도, 세션 길이, 또래 비교, AI 주간 분석, 유형 카드) / **코치**(AI 친구 채팅, 르르 메시지 스레드) / **친구**(감소율 랭킹, 잔소리 맡기기, 함께 챌린지) / **설정**.
- 실시간: 숏폼을 보면 화면 가장자리에 **플로팅 르르**(경과 링) → 맥락별 임계값에서 **카드 1단계** → '5분만 더' → 5분 뒤 **메시지 알림 헤드업 + 카드 2단계(이유 칩)** → **3단계(10초 숨 고르기 + "5분만 더 볼게" 입력)** → **4단계(연장 3분, 질문 답하기)**. 하루 연장 3회 초과 시 다음 세션은 2단계부터.
- 맥락 8개: 기상 직후·이동·수업/업무 중·식사·쉬는 시간·자기 전·한밤중·기타 (하루 리듬 + 기기 신호 + 확신도, 낮으면 AI가 확인 질문).
- 지표: 기준선 대비 감소율, 알림 이후 멈춘 비율, 위험 맥락 시간, 기록률.

## 5. 기술 스택 (고정 — 버전은 `docs/03_ARCHITECTURE.md` 6절 표)
| 영역 | 선택 |
|---|---|
| 언어·빌드 | Kotlin, Gradle Kotlin DSL, 버전 카탈로그, `build-logic` 컨벤션 플러그인, KSP(kapt 금지). **AGP 9의 내장 Kotlin**을 쓴다(안드로이드 모듈에 `org.jetbrains.kotlin.android` 플러그인을 적용하지 않는다) |
| UI | Jetpack Compose + Material 3, 커스텀 토큰(`PauseTheme`), Pretendard(키트의 `pause_sans_variable.ttf`), 차트는 직접 `Canvas`(차트 라이브러리 쓰지 않음) |
| 내비게이션 | **Navigation 3** (`androidx.navigation3` 1.2.1, `lifecycle-viewmodel-navigation3`). 경로 키는 `@Serializable` + `NavKey`, 백 스택은 `rememberNavBackStack`으로 `app`이 가진다. Navigation Compose(2.x)는 유지보수 모드라 쓰지 않는다 |
| 구조 | 단방향(UI → ViewModel `StateFlow` → Repository), Hilt DI |
| 비동기 | Coroutines + Flow |
| 저장 | Room 2.8(스키마 내보내기. Room 3은 쓰지 않는다), DataStore Preferences |
| 백그라운드 | 포그라운드 서비스(감지·개입), WorkManager(예약·리포트 알림·정리) |
| 직렬화 | kotlinx.serialization |
| AI | Firebase AI Logic(Gemini Developer API) + App Check, ML Kit GenAI Prompt API(선택), Mock |
| 원격(P1) | Firebase Auth(익명), Firestore, Remote Config |
| 테스트 | JUnit4, kotlinx-coroutines-test, Turbine, Robolectric(필요한 곳), Compose UI Test. Mock 라이브러리보다 Fake 우선 |
| 품질 | Spotless + ktlint, Android Lint |

## 6. 작업 방식 (긴 작업을 끝까지 해내는 법)
1. **시작 점검**: `git status`가 깨끗한지, JDK 버전, Android SDK 경로(`ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`), `./gradlew --version`이 되는지 확인한다. 윈도우면 Claude Code는 Git Bash로 명령을 돌리므로 `./gradlew`를 쓴다(PowerShell이면 `.\gradlew.bat`). Gradle wrapper는 키트에 있다(Gradle 9.8.2, 첫 실행 때 내려받음). 첫 빌드가 되면 `./gradlew wrapper --gradle-version 9.8.2`를 한 번 돌려 wrapper jar를 같은 버전으로 맞추고 커밋한다. `local.properties`가 없으면 `ANDROID_HOME` 또는 `%LOCALAPPDATA%\Android\Sdk`로 `sdk.dir`을 만든다(윈도우 경로는 `sdk.dir=C\:\\Users\\이름\\AppData\\Local\\Android\\Sdk`처럼 이스케이프, 커밋하지 않는다). `JAVA_HOME`이 없어서 `./gradlew`가 안 되면 멈추고 사람에게 "KIT_GUIDE.md 시작 순서 0-3번(환경 변수)을 해 달라"고 한 줄로 요청한다(이것만 예외적으로 묻는다). 앱 코드는 아직 없으니 `app/`과 모든 모듈은 0단계에서 새로 만든다.
2. **단계 순서대로**(7절). 한 단계 안에서는 모듈 단위로 작업하고 모듈마다 커밋한다. 커밋 메시지는 Conventional Commits(`feat(core-sensing): …`).
3. **각 단계 끝에 반드시**: `./gradlew spotlessApply` → `./gradlew check assembleDebug` 통과 → `docs/PROGRESS.md` 체크 → `docs/BUILD_REPORT.md` 갱신 → `chore(phase-N): 완료` 커밋.
4. **질문하지 말고 가정한다.** 가정은 `BUILD_REPORT.md`의 "가정" 절에 적는다. 단, 새 권한·계약 변경·개인정보 관련 결정은 ADR(`docs/decisions/ADR-00NN-*.md`)로 남긴다.
5. **막히면**: 같은 오류로 세 번 실패하면 인터페이스 뒤에 Fake를 두고 `// TODO(BUILD_REPORT#n): 이유`로 넘어간 뒤 보고서에 적는다. 빌드를 깨진 채로 다음 단계로 가지 않는다.
6. **컨텍스트 관리**: 파일을 한 번에 너무 크게 쓰지 않는다(파일당 400줄 이하 목표). 단계가 끝날 때 무엇을 했는지 보고서에 요약해 두면, 새 세션이 그것만 읽고 이어갈 수 있다.
7. **빌드를 돌릴 수 없는 환경**이면 코드를 끝까지 쓰되 보고서에 "미검증"과 사람이 돌릴 명령을 적는다.
8. **버전을 올리지 않는다.** AGP·Gradle·Kotlin과 라이브러리는 03 문서 6절 표의 값을 쓴다(Gradle은 키트의 wrapper 9.8.2). 실제로 쓴 값은 `libs.versions.toml`과 `docs/decisions/ADR-0007-versions.md`에 기록한다. 표의 라이브러리가 해결되지 않으면(빌드 실패) 그 하나만 바로 아래 안정판으로 내리고 같은 ADR에 적는다.

## 7. 단계별 지시

### 0단계 · 저장소 기반
- 03 문서 7절 구조를 처음부터 만든다(앱 코드는 아직 없다): `build-logic/convention`(플러그인 ID `pause.android.application`, `pause.android.library`, `pause.android.library.compose`, `pause.android.feature`, `pause.android.hilt`, `pause.jvm.library`), 20개 모듈 생성(키트에 있는 모듈별 `AGENTS.md` 위치 그대로), `settings.gradle.kts`에 전부 `include`.
- 순수 JVM 모듈(`core:model`, `core:common`, `core:insights`, `core:ai-engine`)은 `pause.jvm.library`. 나머지 core는 안드로이드 라이브러리, feature는 `pause.android.feature`(Compose + Hilt + designsystem + data 기본 의존).
- `gradle/libs.versions.toml` 작성, 루트에 Spotless(ktlint) 적용, `.editorconfig`(키트) 사용.
- `app`: `@HiltAndroidApp` `PauseApp`, `MainActivity`(`enableEdgeToEdge()`), 하단 탭 5개 빈 화면(오늘·리포트·코치·친구·설정), 온보딩 게이트(프로필 없으면 온보딩). `strings.xml`에 `app_name`=사르르, `buddy_name`=르르.
- 앱 아이콘: 적응형 아이콘(배경 `#C6F24E`, 전경 = `designsystem_buddy_body`를 가운데 66dp 안에 둔 `inset` + 기본 얼굴, monochrome = 실루엣 벡터). 얼굴을 넣은 전경 PNG는 1단계에서 `BuddyBitmap`으로 한 번 구워 `app/src/main/res/mipmap-*`에 커밋해도 된다.
- `google-services` 플러그인은 **`app/google-services.json`이 있을 때만** 적용한다(없어도 빌드되게).
- `:core:ai-engine:aiEval`은 0단계에서 **빈 보고서만 쓰는 `JavaExec` 스텁**으로 먼저 등록한다(CI가 이 작업을 부르므로). 4단계에서 실제 평가로 바꾼다.
- **완료 기준**: `./gradlew spotlessCheck check assembleDebug` 통과, 앱 실행 시 탭 5개 전환, CI(`.github/workflows/android-ci.yml`) 통과.

### 1단계 · 모델·디자인 시스템·시연 데이터
- `core:model`: 04 문서 2절 타입 전부. `core:common`: `AppClock`(java.time.Clock 주입), 디스패처 한정자, `DayBoundary`(새벽 4시), `AppLog` 인터페이스.
- `core:designsystem`: 06 문서 2~6절 토큰을 `PauseColors`(라이트·다크) + Material 3 `ColorScheme` 매핑, `PauseTypography`(키트의 `pause_sans_variable.ttf`, 이 문서 8절 17번), 모양·간격, 컴포넌트 전부, **`DayClock`·`WeekClock`(06 문서 7절)**, **르르 `BuddyAvatar(expression, size, melt)`(06 문서 8절)**: 몸은 키트의 `res/drawable-nodpi/designsystem_buddy_body.webp`(얼굴 없는 각설탕) 한 장, 얼굴 6표정은 `BuddyFaceGeometry`(순수 Kotlin, 06 문서 8절 표의 좌표 그대로)를 `Canvas`로 그린다. 같은 기하로 `BuddyBitmap.render()`(알림 큰 아이콘용 `Bitmap`)도 만든다. 녹는 단계는 그림이 없으면 웅덩이 타원 + 세로 축소로 그린다. 알림 작은 아이콘은 키트의 `designsystem_ic_buddy_silhouette.xml`. 로고는 키트의 `designsystem_wordmark.xml`로 `Wordmark` 컴포넌트를 만들어 오늘 탭 상단 바에 쓴다(글꼴로 다시 치지 않는다, `docs/brand/BRAND.md` 4절). 모든 컴포넌트에 라이트·다크 Preview(르르는 6표정 × 4단계 × 28/56/72dp 미리보기 한 장).
- `core:sensing`의 `DemoScenario`: 14일 × 3패턴(`NIGHT_OWL` 기본 — 목업 05의 일주일 수치와 같게, `COMMUTER`, `DOMINO`), 고정 시드, `source = DEMO`. `assets/demo/friends.json`(가상 친구 3명: 하은·민지·준호), `assets/demo/cohort.json`(연령대·신분별 가상 분포, "시연용" 표시).
- `core:database`: 04 문서 6절 테이블·DAO·인덱스, `exportSchema = true`, KSP `room.schemaLocation`.
- `core:data`: 저장소 인터페이스와 구현, DataStore(프로필·설정·동의 기록).
- `feature:today`: 목업 `01_today_light/dark.png` 구성. 데이터는 저장소에서(하드코딩 금지).
- **완료 기준**: 설정 > 시연 모드 켜기 → 오늘 탭이 목업과 같은 구성으로 라이트·다크 모두 표시, `DemoScenario` 결정성 테스트 통과.

### 2단계 · 감지·세션·맥락·경로·온보딩
- `UsageStatsSource`: 권한 확인(`AppOpsManager`), 화면 켜짐일 때만 `queryEvents` 폴링, 이벤트는 `ACTIVITY_RESUMED`/`ACTIVITY_PAUSED`(`MOVE_TO_*`는 API 29부터 지원 중단)와 `SCREEN_INTERACTIVE`/`SCREEN_NON_INTERACTIVE`, `KEYGUARD_HIDDEN`. API 35 이상은 `queryEvents(UsageEventsQuery)`에 이벤트 종류·패키지를 지정하고(SDK_INT로 분기, 그 아래는 `queryEvents(begin, end)`), **잠금 상태에서는 `null`이 올 수 있으니 처리**한다. 이벤트는 액티비티 단위라 패키지 단위 상태로 묶는다. 이벤트가 늦게 올 수 있으니 매 폴링마다 '최근 N분' 창을 다시 조회해 상태를 다시 만든다.
- `SessionTracker`: 숏폼 앱끼리 60초 이내 전환은 같은 세션(앱별 `AppSegment`), 다른 앱 60초 이상 또는 화면 꺼짐 → 종료. `LiveSession` StateFlow.
- `PathResolver`: 진입(잠금 해제 10초 이내 / 런처 / 직전 앱 종류 / 다른 숏폼), 이탈(화면 끔 10초 / 홈 / 앱 종류 / '그만' 뒤 60초). 앱 종류는 `<queries>` + `ApplicationInfo.category` + `assets/app_kinds.json`.
- `ContextClassifier`: PRD 8절 우선순위 + 확신도. 순수 함수로 만들고 경계값 표 테스트.
- 추적 앱 기본값(설정에서 편집): 유튜브 `com.google.android.youtube`, 인스타그램 `com.instagram.android`, 인스타그램 라이트 `com.instagram.lite`, 틱톡 `com.ss.android.ugc.trill`(한국 스토어 표시본)·`com.zhiliaoapp.musically`(글로벌)·`com.zhiliaoapp.musically.go`·`com.ss.android.ugc.tiktok.lite`. 이 목록을 `<queries><package/></queries>`, 접근성 `packageNames`와 같게 유지한다. 실기기에서 패키지명을 확인해 보고서에 적는다.
- `core:insights`: `ClockBins`, `Baseline`, `ReductionRate`, `Coverage`, `RiskWindows`, `ViewerTypeClassifier`(05 문서 5.6), `WeeklyObservations`(관찰 ID o1..), `MeltStageCalculator`(06 문서 8절, 오늘 숏폼 ÷ 기준 → 0~3). 전부 단위 테스트.
- `feature:onboarding`: 목업 09·10 스타일. 6단계(프로필 → 하루 리듬 → 목표·관심사·말투 → 추적 앱 → 권한 → AI 동의). 권한마다 `PermissionExplainer` 고지 화면 후 설정 화면으로 보내고, 돌아오면 상태를 다시 읽는다. **만 18세 미만 선택 시 이용 안내 후 종료**(ADR-0009, 프로필에 저장하지 않음).
- **완료 기준**: 실기기에서 유튜브를 몇 분 보고 나오면 세션 1개가 맥락·진입·이탈과 함께 저장된다(설정 > 개발자 메뉴에 최근 세션 목록). 분류기·경로·인사이트 테스트 통과.

### 3단계 · 실시간 개입
- `MonitorService`: 포그라운드 서비스(**`foregroundServiceType="specialUse"` 하나만**, 권한 `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`, `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="사용자가 켠 동안 숏폼 앱 사용 시간을 세고 기준 시간에 화면 위 알림을 띄움"/>`. `dataSync`는 데이터 전송용이고 6시간 제한·부팅 시작 금지가 있어 쓰지 않는다). 사용자가 감지를 켤 때(앱이 화면에 있을 때) 시작한다. 화면 켜짐/꺼짐 수신기는 서비스 안에서 런타임 등록.
- `NudgeEngine`: 순수 상태 기계(03 문서 3.2). 입력 = `LiveSession`, 맥락별 임계값, 오늘 연장 횟수, 스누즈, 시계. 출력 = `NudgeCommand`(ShowBubble/ShowCard(level)/PostBuddyMessage/Hide). 모든 전이 단위 테스트.
- `OverlayController`: 플로팅 르르(`BuddyAvatar` 보는 중 + 오늘 녹은 단계, 경과 링, 화면 가장자리에 반쯤 숨어 빼꼼, 끌기, 가장자리 붙기, 탭하면 미니 패널) + 카드 1~4단계(목업 02·03·04). 3단계는 텍스트 입력이 필요하므로 포커스 가능한 창. '그만'은 홈 인텐트 + 칭찬 + `NudgeEvent`.
- `PauseNotifier`: 채널 4개, 메시지 알림 = `MessagingStyle` + `Person`(르르: 이름은 문자열 `intervention_sender_buddy` = `"%1$s · %2$s"`에 `buddy_name`·`app_name`을 넣은 값, 아이콘 `BuddyBitmap.render(CALLING)`; 친구: `intervention_sender_friend` = `"%1$s · 맡겨 둔 잔소리"`) , 작은 아이콘은 실루엣 벡터, 탭하면 코치 채팅 딥링크(대화 단축키는 쓰지 않음, 8절 6번). 친구가 맡긴 잔소리가 있으면 2단계에서 그것을 먼저 쓴다.
- `ReminderScheduler`(WorkManager): 예약 알림(최대 3), 위험 시간대 미리 알림(하루 1), 일요일 리포트 알림, 보관 기간 정리.
- 기본 문구 팩 `core/data/src/main/assets/copy/default_pack.json`: 7맥락 × 단계 × 말투 3, 자리표시자 외 숫자 없음.
- **시연**: 오늘 탭(시연 모드)에 "시연: 숏폼 보기 시작" 버튼. `feature:today`는 `core:data`의 인터페이스 `DemoController`(`startShortformDemo()`, `stop()`)만 부른다. 구현은 `core:intervention`(`DemoEventSource`로 유튜브 세션을 시작하고 개입 타이머만 60배속)에 두고 그 모듈의 `di/`에서 Hilt로 묶는다. 오버레이 권한이 없으면 `core:intervention`의 `NudgePreviewActivity`가 같은 카드를 앱 화면으로 띄운다(feature가 intervention에 의존하지 않게).
- **완료 기준**: 시연 모드에서 PRD 11절 3번 흐름이 끝까지 돈다. 실기기에서 임계값 ±30초 안에 카드가 뜨고 '그만'이 홈으로 간다.

### 4단계 · AI
- `core:ai-engine`(순수 JVM): `LlmClient`, `MockLlmClient`, `RestGeminiClient`(JDK HttpClient, 평가 전용), `PromptRegistry`(`resources/prompts/*.md` 머리말 파싱), 출력 스키마(`@Serializable`), 검증기(05 문서 4·5절), `SafetyFilter`, `BannedWords`, `CoachEngine`(도구 호출 루프, `CoachTools` 인터페이스), `eval/`.
- 프롬프트 v1 5개: `weekly_report_v1.md`, `copy_pack_v1.md`, `context_check_v1.md`, `coach_chat_v1.md`, `poke_draft_v1.md`. 한국어, 말투 변수, 자리표시자 화이트리스트, `<user_text>` 주입 방어, JSON만 출력.
- `core:ai`(안드로이드): `CloudLlmClient` — Firebase AI Logic의 Gemini Developer API 백엔드, JSON 응답 스키마, 채팅은 함수 선언, App Check(디버그 빌드는 디버그 공급자). `Firebase.ai(backend = GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true).generativeModel(modelName = AiConfig.model, generationConfig = generationConfig { responseMimeType = "application/json"; responseSchema = … })`. **2026-11-02부터 Firebase AI Logic은 App Check 적용이 필수**라 App Check 없는 요청은 막힌다(디버그 공급자 토큰은 콘솔에 등록하고 커밋하지 않는다). firebase-ai 18부터 Gemini 3.x에는 `temperature`·`topK`·`topP`를 넣지 않는다(지원 중단) — 프롬프트 머리말의 `temperature`는 Mock·평가용으로만 읽는다. 함수 호출은 앱이 직접 실행하고 `FunctionResponsePart`로 돌려주는 루프(자동 실행 없음). 응답 스키마에서 `optionalProperties`에 넣지 않은 필드는 모두 필수로 취급된다. Firebase가 설정되지 않았으면 `isAvailable = false`로 두고 라우터가 Mock을 쓴다. `OnDeviceLlmClient` — ML Kit GenAI Prompt API `com.google.mlkit:genai-prompt:1.0.0-beta5`(베타, SLA 없음). `checkStatus()` → `download()` 후 사용. **우리 앱이 맨 앞에 있을 때만** 추론할 수 있고 포그라운드 서비스에서 부르면 `BACKGROUND_USE_BLOCKED`, 몰리면 `BUSY`, 하루 한도를 넘으면 `PER_APP_BATTERY_USE_QUOTA_EXCEEDED`. 한국어 지원이 문서에 없어 `context_check` 같은 짧은 작업에만 쓰고, 평가에서 품질을 확인한다. 지원하지 않으면 `isAvailable = false`. `AiRouter`, `AiCallLogger`, `WeeklyReportGenerator`, `CopyPackGenerator`, `PokeDrafter`, `CoachTools` 구현(저장소 조회).
- 화면: 리포트의 AI 주간 분석(목업 06: 관찰과 가설 분리, 근거 칩, 맞아/반쯤/아니야, 전략 고르기, 임계값 제안), 코치 채팅(목업 07: 도구 확인 표시, 근거 칩, 계획 저장 버튼, 추천 질문), 설정 > AI가 한 일. AI가 쓴 문장마다 앱 안 '신고'(8절 25번).
- 평가: `./gradlew :core:ai-engine:aiEval -Pprovider=mock|gemini` → `build/reports/ai-eval/`에 Markdown + CSV. 데이터 세트는 05 문서 9절 구성으로 직접 만든다(가상의 관찰 JSON, 주입 공격 문장, 위기 표현 포함).
- **완료 기준**: Mock으로 리포트·문구 팩·채팅·잔소리 초안이 전부 돈다. 검증기 테스트(좋은 예·나쁜 예) 통과. 평가 보고서 생성. Firebase를 붙이면 실제 모델로도 같은 화면이 돈다.

### 5단계 · 리포트·친구·설정
- `feature:report`: 목업 05 — 일주일 시계(+표 보기 히트맵), 맥락별 합계 막대, 진입→앱→이탈 흐름도(상위 5 + 기타), 세션 길이 분포, 또래 비교(시연 데이터 표시), 유형 카드(공유 이미지는 P1).
- `feature:friends`: 목업 08 — 가상 친구로 감소율 랭킹(상위 3 + 나, 측정 중 표시), 주간 리그 카드(P1은 '곧 열려요'), 잔소리 맡기기(AI 초안, 40자, 금지 표현 필터), 함께 챌린지. `SOCIAL_BACKEND=firebase`일 때만 Firestore 사용(04 문서 7절).
- `feature:settings`: 맥락별 임계값, 예약 알림, 추적 앱, 감지 방식(앱 단위/정밀), AI 제공자, AI가 한 일, 개인정보(내보내기 JSON, 모든 기록 삭제), 테마(시스템/라이트/다크), 시연 모드(패턴 선택, 데이터 넣기/지우기), 개발자 메뉴(최근 세션, 감지 로그).
- **완료 기준**: PRD 11절 시연 시나리오 1~7번이 시연 모드에서 끝까지 돈다.

### 6단계 · 마감
- 접근성: TalkBack 라벨, 글자 200%에서 잘림 없음, 터치 48dp, 색만으로 의미 전달 없음(맥락은 아이콘+이름).
- 다크 모드 전 화면 확인, 화면 꺼짐 시 폴링 중지 확인, 메모리 누수(오버레이 창 제거) 확인.
- (P1, 시간이 남으면) 정밀 모드 `AccessibilitySource` — `isAccessibilityTool`을 선언하지 않는다. `android:packageNames`는 추적 앱 7개로 제한, `accessibilityEventTypes="typeWindowStateChanged|typeViewScrolled|typeViewClicked"`만(넘김·클릭 **횟수**용, `typeAllMask` 금지), `canPerformGestures` 없음, `performGlobalAction` 금지. 화면 구조를 봐야 하면 `canRetrieveWindowContent="true"`로 하고 고지에 '화면 구성 요소 이름 확인'을 적는다. 켜기 전에 **별도 고지 화면**(읽는 것·쓰는 곳·하지 않는 것, '동의하고 설정으로 이동' / '사용 안 함', 거절해도 기본 모드 동작)을 띄운다 — 문안은 `docs/research/policy.md` 3절. Android 17의 고급 보호 모드가 켜져 있으면(`AdvancedProtectionManager`, 권한 `QUERY_ADVANCED_PROTECTION_MODE`) 정밀 모드를 끄고 기본 모드로 안내한다. 이 기능을 넣으면 Play Console 접근성 선언과 동영상(거절 흐름 포함)이 필요하다는 것을 `BUILD_REPORT.md`에 적는다.
- `README.md`(빌드·실행·시연 방법), `docs/BUILD_REPORT.md` 최종본.

## 8. 실수하기 쉬운 곳 (반드시 지킨다)
1. **오버레이 안의 Compose**: 서비스에는 LifecycleOwner가 없다. `ComposeView`에 `setViewTreeLifecycleOwner`, `setViewTreeSavedStateRegistryOwner`(필요하면 `ViewModelStoreOwner`)를 직접 붙이는 작은 `OverlayLifecycleOwner`를 만들고, 창을 붙일 때 RESUMED, 뗄 때 DESTROYED로 옮긴다.
2. **오버레이 창 플래그**: 버블은 `TYPE_APPLICATION_OVERLAY` + `FLAG_NOT_FOCUSABLE` + `wrap_content`(아래 앱 터치를 막지 않게). 카드 3단계는 입력이 있으므로 포커스 가능 + `SOFT_INPUT_ADJUST_RESIZE`. 전체 화면 반투명 창으로 터치를 통과시키려 하지 않는다(Android 12부터 `TYPE_APPLICATION_OVERLAY`는 신뢰하지 않는 창이라, 합친 불투명도가 0.8을 넘는 창 아래로 통과한 터치는 차단된다. 버블과 카드는 자기 터치를 받는 작은 창으로 만든다. 다른 앱이 `setHideOverlayWindows(true)`로 오버레이를 숨길 수 있으니 안 보여도 앱이 망가지지 않게 한다).
3. **포그라운드 서비스**: ① **사용자가 화면에서 '감지 켜기'를 누를 때만 시작**한다. WorkManager·알림 수신기·백그라운드에서 `startForegroundService()`를 부르면 `ForegroundServiceStartNotAllowedException`. ② Android 15부터 오버레이 권한만으로는 백그라운드 시작 예외가 안 된다 — 오버레이 창이 **실제로 보이는 중**이어야 한다. ③ 타입은 `specialUse` 하나. 다른 타입(특히 `dataSync`)을 섞으면 부팅 후 시작·시간 제한에 걸린다. ④ 부팅 후(`BOOT_COMPLETED`, 잠금 해제 뒤) 사용자가 감지를 켜 둔 상태였으면 다시 시작할 수 있다(`specialUse`는 부팅 시작 금지 목록에 없음). ⑤ 사용자가 작업 관리자에서 '중지'하거나(앱 전체가 멈춤, 콜백 없음) 상태 알림을 스와이프로 지울 수 있다(14+) → 다음 실행 때 `ApplicationExitInfo.REASON_USER_REQUESTED`를 확인하고 오늘 탭에 '감지가 꺼져 있어' 카드를 띄운다. ⑥ 화면이 꺼지면 폴링을 멈춘다. ⑦ Android 16부터 서비스가 돌아도 WorkManager 할당량은 그대로 적용된다. ⑧ Play Console의 포그라운드 서비스 선언(설명·중단 시 영향·사용자가 켜는 동영상)과 매니페스트 `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` 문구가 서로 같아야 심사를 통과한다 — 제출용 문안을 `BUILD_REPORT.md`에 적는다.
4. **사용 기록**: 권한은 `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, packageName)`로 확인하고, 설정 화면은 `Settings.ACTION_USAGE_ACCESS_SETTINGS`. 앱의 `onResume`마다 권한을 다시 확인하고, 꺼져 있으면 서비스를 멈추고 고지 화면 → 설정으로 안내한다. 다른 앱 사용 기록은 Play 사용자 데이터 정책의 민감 정보라 **앱 안 고지(권한 화면으로 보내기 직전, 다른 고지와 묶지 않음, 뒤로 가기는 동의가 아님)** + 개인정보처리방침 + Data safety가 필요하다. `QUERY_ALL_PACKAGES`는 쓰지 않는다.
5. **패키지 가시성**: `<queries>`에는 추적 앱 7개의 `<package>`와 MAIN/LAUNCHER `<intent>`만. `QUERY_ALL_PACKAGES` 금지.
6. **알림**: 33+에서 `POST_NOTIFICATIONS` 런타임 요청, 보내기 전 `areNotificationsEnabled()`. 헤드업은 채널 중요도 HIGH. 메시지 알림은 `NotificationCompat.MessagingStyle` + `Person`만 쓰고 **대화 단축키(`pushDynamicShortcut`, `setShortcutId`)는 붙이지 않는다**: 안드로이드의 '대화' 섹션은 사람 사이의 실시간 대화용이라 AI 캐릭터 알림을 거기 올리지 않는다. 보낸 사람 이름은 사람으로 오해하지 않게 `intervention_sender_buddy`("르르 · 사르르"), 친구 잔소리는 `intervention_sender_friend`("민지 · 맡겨 둔 잔소리"). 개입 알림은 짧은 간격으로 연달아 보내지 않고(같은 세션에 1개를 갱신), 채널·그룹 키를 나눈다.
7. **'그만' 동작**: `Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(FLAG_ACTIVITY_NEW_TASK)`. 접근성 `performGlobalAction`을 쓰지 않는다.
8. **정확한 알람 안 씀**: `SCHEDULE_EXACT_ALARM`/`USE_EXACT_ALARM` 선언 금지. WorkManager로 충분하다.
9. **Hilt**: 서비스·접근성 서비스는 `@AndroidEntryPoint`. 워커는 `androidx.hilt:hilt-work` + `HiltWorkerFactory`, 매니페스트에서 기본 WorkManager 초기화를 제거.
10. **Room**: KSP, `exportSchema = true`, `room.schemaLocation` 인자, 파괴적 마이그레이션 금지.
11. **DataStore**: 파일당 인스턴스 하나(Hilt 싱글턴).
12. **Firebase가 없을 때**: `google-services.json`이 없으면 플러그인을 적용하지 않고, `FirebaseApp` 초기화 여부를 확인해 클라우드 AI·소셜을 끈다. 앱은 절대 크래시하지 않는다.
13. **App Check**: 디버그 빌드는 디버그 공급자(토큰을 콘솔에 등록), 릴리스는 Play Integrity.
14. **엣지 투 엣지**: targetSdk가 35 이상이면 엣지 투 엣지가 강제된다. `Scaffold`/`WindowInsets`로 상태 표시줄·내비게이션 바 영역을 처리한다.
15. **시간**: 저장은 `Instant`, 표시·맥락 계산만 로컬 시간. 하루 경계는 새벽 4시. 시연 배속은 개입 타이머와 시연 이벤트에만 적용(WorkManager에는 적용하지 않음).
16. **문구**: 모든 한국어 문구는 모듈별 `strings.xml`. 캐릭터 문구는 문구 팩 JSON.
17. **폰트**: 키트에 `core/designsystem/src/main/res/font/pause_sans_variable.ttf`(Pretendard 서브셋 가변 글꼴)가 있다. 굵기별로 `FontVariation.Settings(FontVariation.weight(n))`을 준 `Font`를 여러 개 등록해 `FontFamily`를 만든다. 파일 이름·라이선스(`licenses/Pretendard-OFL.txt`)를 바꾸지 않는다.
18. **제조사 절전**: 삼성 등은 백그라운드를 강하게 막는다. 온보딩 끝에 앱 절전 예외 설정 안내 화면(직접 예외 요청 대신 설정 화면으로 이동).
19. **접근성 서비스(정밀 모드)**: `isAccessibilityTool`을 선언하지 않는다. `packageNames`를 추적 앱으로 제한, 이벤트는 창 상태 변경·스크롤·클릭만, 화면 글자는 판별에만 쓰고 저장하지 않는다. 앱 안 고지 화면을 반드시 먼저 띄운다.
20. **AGP 9 내장 Kotlin**: 안드로이드 모듈에 `org.jetbrains.kotlin.android`를 적용하지 않는다(적용하면 빌드 오류). 순수 JVM 모듈만 `org.jetbrains.kotlin.jvm`. Compose 컴파일러 플러그인 `org.jetbrains.kotlin.plugin.compose`와 직렬화 플러그인은 Kotlin과 같은 버전. KSP는 2.3.x(Kotlin과 따로 번호). kapt는 못 쓴다.
21. **JDK**: Gradle은 JDK 21로 돌리고(Robolectric이 SDK 36 이상에서 JDK 21 요구), `compileOptions`/`jvmTarget`은 17.
22. **Navigation 3**: 화면마다 ViewModel 범위를 나누려면 `NavDisplay`의 `entryDecorators`에 `rememberSaveableStateHolderNavEntryDecorator()`와 `rememberViewModelStoreNavEntryDecorator()`를 넣는다(정확한 이름은 Navigation 3 1.2 문서로 확인). `hiltViewModel()`은 `androidx.hilt:hilt-lifecycle-viewmodel-compose`. 하단 탭은 탭별 백 스택을 `app`이 가진다.
23. **targetSdk 36 동작**: 엣지 투 엣지를 끌 수 없다(14번). 예측형 뒤로 가기가 기본이라 `onBackPressed`가 불리지 않는다 → `BackHandler`/`OnBackPressedCallback`. 600dp 이상 화면에서 방향·크기 고정이 무시되니 세로 고정을 가정하지 않는다.
24. **'그만'의 홈 이동**: 오버레이에서 `PendingIntent`가 아니라 직접 `startActivity(homeIntent)`로 연다(오버레이 권한이 백그라운드 액티비티 시작 예외).
25. **AI 출력 신고**: Play AI 생성 콘텐츠 정책에 따라 AI가 쓴 문장마다 앱 안 '신고' 동작(05 문서 7절).
26. **연령**: 온보딩 첫 화면에서 만 18세 미만이면 종료(ADR-0009). AI 동의 화면에 "입력이 Google 서버(국외)로 가고, 무료 등급에서는 서비스 개선에 쓰일 수 있음"을 적는다(04 문서 5절).
27. **브랜드 이름**: Kotlin 코드에 '사르르'·'르르'를 쓰지 않는다. `R.string.app_name`, `R.string.buddy_name`, 문구 팩의 `{buddy}` 자리표시자만.

## 9. 시연 수용 테스트 (사람이 따라 하는 체크리스트로 README에 넣는다)
PRD 11절 1~7번을 그대로 적고, 각 단계에서 무엇이 보여야 하는지(목업 파일 이름)를 적는다. 6단계 끝에 직접 따라 해 보고 결과를 `BUILD_REPORT.md`에 적는다.

## 10. `docs/BUILD_REPORT.md` 형식
```markdown
# 빌드 보고서
## 상태 요약 (단계별 ✅/⚠️/❌ 한 줄씩)
## 실행 방법 (명령, 시연 모드 켜는 법, Firebase 붙이는 법)
## 실제로 쓴 버전 (libs.versions.toml 요약)
## 가정
## 계획과 달라진 점 (이유)
## 남은 TODO (파일·이유)
## 테스트 결과 (모듈별 개수, 실패 0인지)
## AI 평가 요약 (제공자, 스키마·검증 통과율, 금지 표현·숫자 위반, 지연)
## 실기기 확인 (기기·OS, 확인한 패키지명, 임계값 오차)
```

이제 2절의 문서를 순서대로 읽고, 0단계부터 시작해.
