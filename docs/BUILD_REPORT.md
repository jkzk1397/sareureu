# 빌드 보고서

## 상태 요약 (단계별 ✅/⚠️/❌ 한 줄씩)
- 0단계 저장소 기반: ✅ `spotlessCheck check assembleDebug` 로컬 통과, 에뮬레이터에서 온보딩 → 탭 5개 전환 → 시연 스위치 확인. 원격 CI는 PR 후 확인.
- 1단계 모델·디자인 시스템·시연 데이터: ✅ 설정 > 시연 모드 켜기 → 오늘 탭이 목업 01 구성으로 라이트·다크 모두 표시(에뮬레이터 API 37에서 확인). `DemoScenario` 결정성 테스트 통과.
- 2단계 감지·세션·맥락·경로·온보딩: ✅ 로직·테스트 완료(61개 통과), 에뮬레이터에서 온보딩 전 과정(연령 → 고지 → 리듬 → 권한 왕복 → AI 동의 → 완료) 확인. 실기기 세션 저장은 3단계 `MonitorService`가 `SensingPipeline`을 켜야 돌아서 "사람이 할 확인"으로 넘김.
- 3~6단계: 시작 전

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

16. **2단계 맥락 분류**: 주말 리듬은 기상=그날이 토·일, 취침=다음 날이 토·일(금·토 밤)일 때 주말 값. 취침이 정오 전(예 00:30)이면 다음 달력 날짜. 우선순위 이동 > 식사 > 수업·업무(PRD 8절). 기상 직후 = 첫 잠금 해제 뒤 45분(기상 ±2시간), 모르면 기상 시각부터 45분(확신도 중간). 21시 이후 충전 중이면 자기 전(중간). 확신도 높음=한밤중·자기 전·이어폰 낀 이동, 중간=이동·수업·쉬는 시간, 낮음=식사·기타. 기기 신호는 세션 시작 때 한 번 읽음.
17. **세션·경로**: 세션 끝 = 숏폼을 떠난 시각(60초 안 복귀 공백은 포함). 진입 우선순위 잠금 해제 10초 > 다른 숏폼 > 런처 > 직전 앱 종류, 이탈 '그만' 뒤 60초 > 떠난 뒤 10초 안 화면 끔 > 런처 > 다음 앱 종류. 앱 단위 모드에선 숏폼끼리 바로 넘어가면 같은 세션이라 `FromShortform` 진입은 드물다. 감지를 멈추면 진행 중 세션은 저장하지 않는다.
18. **UsageStats**: 패키지 단위 맨 앞 앱 = 마지막 RESUMED. 매 폴링 최근 3분 창 재조회(숏폼 중 2초·그 외 5초), 감지 시작 전 기록으로 세션을 만들지 않음, 늦게 온 이벤트는 한 번 고쳐 냄.
19. 프로필이 없을 때 기본 하루 리듬(대학생 가정): 평일 07:30 기상·00:30 취침, 주말 09:30·01:30, 08–09·18–19 이동, 09–18 수업, 12–13·18:30–19:30 식사. 프로필이 있으면 `ProfileDayRhythmProvider`가 그 리듬을 쓴다.
20. **관찰 ID**: 05 문서가 o1..o12의 뜻을 정하지 않아서 고정했다 — o1 주간 합계, o2 감소율, o3 1위 맥락, o4 2위 맥락, o5 밤 비중, o6 위험 시간대, o7 1위 진입, o8 1위 흐름, o9 세션 길이, o10 개입 결과, o11 기록률, o12 1위 맥락의 진입. 관찰에는 숫자와 코드 라벨만 있고 문장은 화면·프롬프트에서 만든다. AI에 넘길 만한지(`enoughForAi`)는 관찰 3개 이상 + 기록률 50% 이상.
21. **온보딩**: 13개 화면을 6칸 진행 막대로 묶었다. 시간은 시간 선택기 대신 ±30분 버튼. 수업·근무·통학은 월~금만. 리듬 링에서 21시~(취침−60분)은 기타. 01 문서 7.1의 "지난 7일 기록 불러오기 → 첫 인사이트"는 만들지 않았다(감지가 3단계부터 돌아서). 관심사 프리셋은 온보딩 string-array. 권한 고지 문구 버전 `NOTICE_VERSION = 1`로 동의 기록. 0단계 `onboarding_done`만 있던 설치본은 온보딩을 다시 거친다.
22. 맥락별 임계값은 5·10·15·20·30분 중에서 고른다(기본 한밤중 10, 나머지 15).
23. 알림·다른 앱 위에 표시 권한은 그 기능의 주인인 `core:intervention`의 Manifest에 선언했다(03 문서 5절).

## 계획과 달라진 점 (이유)
- `core:model`·`core:common`과 디자인 토큰(`PauseTheme`)을 0단계 커밋에 먼저 넣었다. 앱 셸과 설정 화면이 토큰을 쓰기 때문이다(1단계 범위를 앞당겼을 뿐 내용은 같다).
- `core:insights`의 `ClockBins`·`Baseline`·`ReductionRate`·`MeltStageCalculator`·`RiskWindows`(2단계 항목)를 1단계에서 먼저 만들었다. 오늘 탭이 계산을 직접 하지 않고 저장소 값만 그리게 하려면 필요했다. 2단계에서 `Coverage`·`ViewerTypeClassifier`·`WeeklyObservations`를 더하고 테스트를 넓힌다.
- 시연 데이터를 넣는 구현(`AssetDemoDataSeeder`)은 `core:sensing`에, 인터페이스(`DemoDataSeeder`)는 `core:data`에 둬서 `feature:settings`가 sensing에 의존하지 않는다(AGENTS.md 4절 표).

## 남은 TODO (파일·이유)
1. ~~온보딩 완료 표시만 저장~~ → 2단계에서 해결(프로필·동의 저장, 녹는 단계 기준 = 프로필 하루 목표).
2. `feature/{report,coach,friends}/...Screen.kt`, `feature/settings/SettingsScreen.kt` — 빈 화면이거나 일부 항목만 있다. 4·5단계에서 채운다.
3. `core/data/.../insight/InsightRepository.kt` — 기록률을 1.0으로 본다. `Coverage` 계산은 2단계에서 만들었지만, '감지가 살아 있던 구간'을 기록하는 쪽(3단계 `MonitorService`)이 아직 없다.
4. ~~`DEFAULT_THRESHOLD_MIN`~~ → 2단계에서 맥락별 임계값 설정으로 바꿈.
5. `core/designsystem/.../buddy/BuddyAvatar.kt` — 시스템 '애니메이션 제거'를 아직 따르지 않는다(녹는 단계 전환이 항상 600ms). 라이트 모드 그림자는 흐림 없는 1dp 오프셋(minSdk 29라 RenderEffect 블러를 못 씀). 6단계 접근성 점검 때 고친다.
6. `core/designsystem/.../chart/FlowDiagram.kt` — 앱 칸 라벨이 띠와 겹칠 수 있다. 5단계 리포트에서 다듬는다.
7. 원격 CI(`.github/workflows/android-ci.yml`) 통과는 PR을 올린 뒤 확인해야 한다.
9. `SensingPipeline`은 아직 아무도 켜지 않는다. 3단계 `MonitorService`가 `start`/`stop`, '그만' 때 `onNudgeStop`을 불러야 한다.
8. 로컬에서 `spotlessApply`가 다른 작업과 함께 돌 때 가끔 실패했다가 다시 돌리면 통과했다(재현 안 됨). CI는 `spotlessCheck`를 따로 돌린다.

## 테스트 결과 (모듈별 개수, 실패 0인지)
- 2단계 끝: 합계 61개, 실패 0 — `core:common` 4 · `core:insights` 20 · `core:database` 1 · `core:sensing` 23(맥락 경계값 30케이스 표 포함, 경로, 세션 추적, 이벤트 정리, 시연) · `core:designsystem` 7 · `core:data` 2 · `feature:onboarding` 4
- 1단계 끝: 합계 26개, 실패 0 (`./gradlew check`)
- `core:common` 4 (DayBoundary) · `core:insights` 9 (ClockBins 3, MeltStage 2, Baseline·감소율 3, RiskWindows 1) · `core:database` 1 (SessionDao, Robolectric) · `core:sensing` 5 (DemoScenario: 결정성, 목업 05 수치, 기준선 23%, 오늘 = 목업 01, 겹침 없음) · `core:designsystem` 7 (BuddyFaceGeometry)
- Android Lint: 오류 0. 경고 1개는 키트 파일 `designsystem_wordmark.xml`의 VectorPath(경로가 김), 손대지 않았다.

## AI 평가 요약 (제공자, 스키마·검증 통과율, 금지 표현·숫자 위반, 지연)
- 0단계 스텁: mock, 항목 0(빈 보고서). 4단계에서 실제 평가.

## 사람이 할 확인
> 에이전트가 할 수 없는 것(실기기·실제 권한·Firebase·스토어). 단계마다 여기에 모은다.
- **2단계 감지(실기기)**: 3단계에서 감지 켜기가 생긴 뒤 유튜브를 몇 분 보고 나와서 설정 > 개발자 메뉴에 세션 1개가 맥락·진입·이탈과 함께 생기는지.
- 추적 패키지 7개가 실제와 맞는지(특히 한국 스토어 틱톡 `com.ss.android.ugc.trill`).
- 그 기기의 런처 패키지가 `core/sensing/src/main/assets/app_kinds.json`에 없으면 '홈' 진입·이탈이 앱 종류 OTHER로 기록될 수 있다 → 표에 추가.
- 잠금 상태에서 `queryEvents`가 null일 때 멀쩡한지, `KEYGUARD_HIDDEN`이 잠금 해제마다 오는지, 화면을 끄면 폴링이 멈추는지(배터리).
- 삼성 등에서 화면을 다시 켰을 때 `ACTIVITY_RESUMED`가 다시 오는지('잠금 해제' 진입이 잡히는지).
- **2단계 온보딩(실기기)**: 사용 기록 설정을 열고 허용한 뒤 돌아오면 "허용됐어"가 뜨는지(에뮬레이터에서는 확인함, 삼성은 목록에서 앱을 찾아야 할 수 있음), Android 13+에서 알림 권한 팝업, 오버레이 설정 화면 열림·복귀 후 감지, 삼성에서 절전 설정 목록이 열리는지.
- 고지 문구 전부를 Play 눈에 띄는 고지(prominent disclosure) 정책과 대조해 사람이 읽어 보기.

## 실기기 확인 (기기·OS, 확인한 패키지명, 임계값 오차)
- 실기기 없음. 에뮬레이터 `Medium_Phone_API_37.0`(Android 17)에서 0·1단계 화면만 확인했다. 감지·개입 실측은 2·3단계.
