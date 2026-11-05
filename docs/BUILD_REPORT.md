# 빌드 보고서

## 상태 요약 (단계별 ✅/⚠️/❌ 한 줄씩)
- 0단계 저장소 기반: ✅ `spotlessCheck check assembleDebug` 로컬 통과, 에뮬레이터에서 온보딩 → 탭 5개 전환 → 시연 스위치 확인. 원격 CI는 PR 후 확인.
- 1단계 모델·디자인 시스템·시연 데이터: ✅ 설정 > 시연 모드 켜기 → 오늘 탭이 목업 01 구성으로 라이트·다크 모두 표시(에뮬레이터 API 37에서 확인). `DemoScenario` 결정성 테스트 통과.
- 2~6단계: 시작 전

## 실행 방법 (명령, 시연 모드 켜는 법, Firebase 붙이는 법)
- 빌드: `./gradlew assembleDebug` (PowerShell은 `.\gradlew.bat assembleDebug`)
- PR 전 검사: `./gradlew spotlessApply` → `./gradlew spotlessCheck check assembleDebug`
- AI 평가(0단계 스텁): `./gradlew :core:ai-engine:aiEval -Pprovider=mock` → `core/ai-engine/build/reports/ai-eval/`
- 설치: `./gradlew installDebug`
- 시연 모드: 처음 실행 → "시작하기" → 하단 탭 '설정' → 시연 모드 켜기(지금 시각 기준 2주치 가상 기록을 넣는다) → '오늘' 탭. 패턴(밤형·출퇴근형·알림 도미노형) 고르기, "2주치 가상 기록 넣기"(다시 넣기), "가상 기록 지우기"도 같은 화면.
- Firebase: `app/google-services.json`을 로컬에만 두면 `com.google.gms.google-services` 플러그인이 자동 적용된다(없으면 적용 안 함, 커밋 금지).
- `local.properties`(커밋 안 함): `sdk.dir=C\:\\Users\\<이름>\\AppData\\Local\\Android\\Sdk`

## 실제로 쓴 버전 (libs.versions.toml 요약)
03 문서 6절 표와 같다: AGP 9.5.0(내장 Kotlin), Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, Navigation 3 1.2.1, lifecycle 2.11.0, Room 2.8.5, DataStore 1.3.0, WorkManager 2.12.0, Hilt 2.60.1, androidx.hilt 1.4.0, Firebase BoM 35.1.0, Robolectric 4.17, Spotless 8.11.0 + ktlint 1.8.0. SDK 29 / 36 / 37.
- 표와 다른 값: 없음. 이 PC에서는 Gradle을 Android Studio 내장 JBR **25**로 돌렸다(표는 JDK 21, ADR-0007 "실제 값"에 기록). 바이트코드는 17. CI는 temurin 21.

## 가정
1. `app_name`·`buddy_name`은 `app`이 아니라 **`core:designsystem`의 `strings.xml`** 에 둔다. feature 모듈이 `app`의 `R`을 볼 수 없어서다. 이 두 키만 모듈 접두사 규칙의 예외. 앱 매니페스트의 `@string/app_name`은 병합된 리소스를 읽는다.
2. `CardCopy`의 Kotlin 속성은 `l1`·`l2`·`l3`·`l4Question`, `@SerialName`으로 JSON 키 `L1`·`L2`·`L3`·`L4Question`을 그대로 쓴다(ktlint 이름 규칙). JSON 계약은 같다.
3. 04 문서 2절 목록에 없는 타입(`AppSettings`, `ThemeMode`, `DemoPattern`, `TodaySummary`, `ReceivedPoke`)은 `core:model`이 아니라 `core:data`에, 계산 결과 타입(`ClockBins`, `DayStat`, `RiskWindow`)은 `core:insights`에 둔다. 계약 PR이 필요 없게.
4. Hilt가 테스트 소스를 만들어서, 테스트가 아직 없는 안드로이드 모듈에서 Gradle 9가 "테스트를 못 찾음"으로 실패한다. 컨벤션 플러그인에서 `failOnNoDiscoveredTests = false`(테스트를 끄는 설정이 아님).
5. 0단계 온보딩은 "시작하기" 버튼 하나로 완료 표시만 저장한다. 실제 6단계 흐름은 2단계.
6. 앱 아이콘 전경은 PNG 대신 **몸 그림 + 벡터 얼굴(`app_ic_launcher_face.xml`) layer-list**. 얼굴 벡터는 `docs/brand/ruru_face_reference.py`와 같은 식(66dp 기준)으로 만들었다.
7. 탭 아이콘 5개와 종 아이콘은 단색 벡터(Material Symbols 모양, Apache 2.0)를 각 모듈 `res/drawable`에 직접 넣었다(의존성 추가 없음).
8. **시연 날짜 구성**: 기준선 주(오늘-14 ~ 오늘-8) + 리포트 주(오늘-7 ~ 오늘-1) + 오늘(지금 시각까지). 시연 모드를 켤 때마다 지금 시각 기준으로 다시 넣는다. 가장 무거운 밤(70분 세션)은 오늘과 같은 요일에 둬서 오늘 탭의 위험 시간대 카드가 의미 있게 나온다.
9. **NIGHT_OWL 리포트 주 = 목업 05**: 58세션, 672분(하루 96분), 맥락별 합계(자기 전 208·이동 161·쉬는 시간 114·식사 81·한밤중 60·수업 34·기상 14), 기준선 872분 → 감소율 23%, 개입이 뜬 세션 14개 중 6개 멈춤(43%). 진입 비율(메신저 38%·잠금 해제 24%·홈 21%·다른 숏폼 10%·기타 7%)도 같다. 세션 길이 분포는 41/26/17/12/3%로 목업(41/26/18/11/4)과 1%p 안. 이탈은 '알림 뒤 멈춤'을 멈춘 세션 수(6개, 10%)에 맞췄다(목업 12%). 모두 `DemoScenarioTest`가 확인한다.
10. 시연 개입 기준은 맥락과 상관없이 15분(`DemoScenario.DEMO_THRESHOLD_MIN`). 오늘 탭 '보는 중' 카드의 기준도 15분 고정(3단계에서 맥락별 임계값 설정으로).
11. 위험 시간대: PRD는 "상위 3칸, 최소 3일 등장"이지만 14일이면 같은 요일은 2번뿐이라 **최소 2일**로 뒀다(`RiskWindows.compute(minDaysSeen = 2)`). 오늘 탭은 오늘 요일 칸 중 가장 큰 칸과, 그 절반 이상인 칸(최대 3)만 시계 테두리에 그린다.
12. "어제보다"는 **어제 같은 시각까지**와 비교한다(오전에 어제 하루 전체와 비교하면 늘 줄어든 것처럼 보이므로). 목업의 "▼23분"은 밤 시각 기준 예시다.
13. 기록률(`coverage`)은 2단계 전이라 기록이 있는 날을 100%로 본다(아래 TODO#3).
14. 오늘 탭의 위험 카드 문구에서 목업의 "10분 전에 알려 줄게"는 뺐다. 미리 알림(`PredictiveNudge`)은 3단계 기능이라 아직 지키지 못할 약속이기 때문.
15. 앱 테마를 시스템과 다르게(예: 시스템 라이트 + 앱 다크) 고르면 상태 표시줄 아이콘 색이 바탕과 맞도록 `enableEdgeToEdge`를 테마에 맞춰 다시 부른다.

## 계획과 달라진 점 (이유)
- `core:model`·`core:common`과 디자인 토큰(`PauseTheme`)을 0단계 커밋에 먼저 넣었다. 앱 셸과 설정 화면이 토큰을 쓰기 때문이다(1단계 범위를 앞당겼을 뿐 내용은 같다).
- `core:insights`의 `ClockBins`·`Baseline`·`ReductionRate`·`MeltStageCalculator`·`RiskWindows`(2단계 항목)를 1단계에서 먼저 만들었다. 오늘 탭이 계산을 직접 하지 않고 저장소 값만 그리게 하려면 필요했다. 2단계에서 `Coverage`·`ViewerTypeClassifier`·`WeeklyObservations`를 더하고 테스트를 넓힌다.
- 시연 데이터를 넣는 구현(`AssetDemoDataSeeder`)은 `core:sensing`에, 인터페이스(`DemoDataSeeder`)는 `core:data`에 둬서 `feature:settings`가 sensing에 의존하지 않는다(AGENTS.md 4절 표).

## 남은 TODO (파일·이유)
1. `core/data/.../profile/ProfileRepository.kt`, `feature/onboarding/...` — 온보딩 완료 표시만 저장. 2단계에서 실제 프로필 저장·6단계 흐름으로. 녹는 단계 기준도 지금은 기준선이 없으면 60분(프로필의 `dailyGoalMinutes` 기본값).
2. `feature/{report,coach,friends}/...Screen.kt`, `feature/settings/SettingsScreen.kt` — 빈 화면이거나 일부 항목만 있다. 4·5단계에서 채운다.
3. `core/data/.../insight/InsightRepository.kt` — 기록률을 1.0으로 본다. 2단계 `Coverage`로 바꾼다.
4. `core/data/.../insight/InsightRepository.kt` `DEFAULT_THRESHOLD_MIN` — 3단계 맥락별 임계값 설정으로.
5. `core/designsystem/.../buddy/BuddyAvatar.kt` — 시스템 '애니메이션 제거'를 아직 따르지 않는다(녹는 단계 전환이 항상 600ms). 라이트 모드 그림자는 흐림 없는 1dp 오프셋(minSdk 29라 RenderEffect 블러를 못 씀). 6단계 접근성 점검 때 고친다.
6. `core/designsystem/.../chart/FlowDiagram.kt` — 앱 칸 라벨이 띠와 겹칠 수 있다. 5단계 리포트에서 다듬는다.
7. 원격 CI(`.github/workflows/android-ci.yml`) 통과는 PR을 올린 뒤 확인해야 한다.
8. 로컬에서 `spotlessApply`가 다른 작업과 함께 돌 때 가끔 실패했다가 다시 돌리면 통과했다(재현 안 됨). CI는 `spotlessCheck`를 따로 돌린다.

## 테스트 결과 (모듈별 개수, 실패 0인지)
- 합계 26개, 실패 0 (`./gradlew check`)
- `core:common` 4 (DayBoundary) · `core:insights` 9 (ClockBins 3, MeltStage 2, Baseline·감소율 3, RiskWindows 1) · `core:database` 1 (SessionDao, Robolectric) · `core:sensing` 5 (DemoScenario: 결정성, 목업 05 수치, 기준선 23%, 오늘 = 목업 01, 겹침 없음) · `core:designsystem` 7 (BuddyFaceGeometry)
- Android Lint: 오류 0. 경고 1개는 키트 파일 `designsystem_wordmark.xml`의 VectorPath(경로가 김), 손대지 않았다.

## AI 평가 요약 (제공자, 스키마·검증 통과율, 금지 표현·숫자 위반, 지연)
- 0단계 스텁: mock, 항목 0(빈 보고서). 4단계에서 실제 평가.

## 실기기 확인 (기기·OS, 확인한 패키지명, 임계값 오차)
- 실기기 없음. 에뮬레이터 `Medium_Phone_API_37.0`(Android 17)에서 0·1단계 화면만 확인했다. 감지·개입 실측은 2·3단계.
