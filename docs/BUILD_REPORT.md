# 빌드 보고서

## 상태 요약 (단계별 ✅/⚠️/❌ 한 줄씩)
- 0단계 저장소 기반: ✅ `spotlessCheck check assembleDebug` 로컬 통과, `aiEval` 스텁 동작. 원격 CI는 PR을 올린 뒤 확인(아래 "남은 TODO").
- 1단계 모델·디자인 시스템·시연 데이터: 진행 중
- 2~6단계: 시작 전

## 실행 방법 (명령, 시연 모드 켜는 법, Firebase 붙이는 법)
- 빌드: `./gradlew assembleDebug` (PowerShell은 `.\gradlew.bat assembleDebug`)
- PR 전 검사: `./gradlew spotlessApply` → `./gradlew spotlessCheck check assembleDebug`
- AI 평가(0단계 스텁): `./gradlew :core:ai-engine:aiEval -Pprovider=mock` → `core/ai-engine/build/reports/ai-eval/`
- 설치: `./gradlew installDebug`
- 시연 모드: 처음 실행 → "시작하기" → 하단 탭 '설정' → 시연 모드 켜기
- Firebase: `app/google-services.json`을 로컬에만 두면 `com.google.gms.google-services` 플러그인이 자동 적용된다(없으면 적용 안 함, 커밋 금지).
- `local.properties`(커밋 안 함): `sdk.dir=C\:\\Users\\<이름>\\AppData\\Local\\Android\\Sdk`

## 실제로 쓴 버전 (libs.versions.toml 요약)
03 문서 6절 표와 같다: AGP 9.5.0(내장 Kotlin), Gradle 9.8.2, Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, Navigation 3 1.2.1, lifecycle 2.11.0, Room 2.8.5, DataStore 1.3.0, WorkManager 2.12.0, Hilt 2.60.1, androidx.hilt 1.4.0, Firebase BoM 35.1.0, Spotless 8.11.0 + ktlint 1.8.0. SDK 29 / 36 / 37.
- 표와 다른 값: 없음. 다만 이 PC에서는 Gradle을 Android Studio 내장 JBR **25**로 돌렸다(표는 JDK 21). 바이트코드는 17로 맞췄고 빌드·테스트는 통과했다. CI는 temurin 21.

## 가정
1. `app_name`·`buddy_name`은 `app`이 아니라 **`core:designsystem`의 `strings.xml`** 에 둔다. feature 모듈이 `app`의 `R`을 볼 수 없어서다. 이 두 키만 모듈 접두사 규칙의 예외(AGENTS.md가 키 이름을 정해 두었다). 앱 매니페스트의 `@string/app_name`은 병합된 리소스를 읽는다.
2. `CardCopy`의 Kotlin 속성 이름은 `l1`·`l2`·`l3`·`l4Question`이고, `@SerialName`으로 JSON 키 `L1`·`L2`·`L3`·`L4Question`을 그대로 쓴다(ktlint 속성 이름 규칙). 계약(JSON 모양)은 같다.
3. 설정 묶음 `AppSettings`와 `ThemeMode`는 04 문서 2절 목록에 없어서 `core:model`이 아니라 `core:data`에 둔다.
4. Hilt가 테스트 소스를 생성해서 테스트가 아직 없는 안드로이드 모듈에서 Gradle 9가 "테스트를 못 찾음"으로 실패한다. 컨벤션 플러그인에서 `failOnNoDiscoveredTests = false`로 둔다(테스트를 끄는 설정이 아님).
5. 0단계 온보딩은 "시작하기" 버튼 하나로 완료 표시만 저장한다. 실제 6단계 흐름은 2단계.
6. 앱 아이콘 전경은 PNG를 굽는 대신 **몸 그림 + 벡터 얼굴(`app_ic_launcher_face.xml`) layer-list**로 만들었다. 얼굴 벡터는 `docs/brand/ruru_face_reference.py`와 같은 식(66dp 기준)으로 생성했다.
7. 탭 아이콘 5개는 `app/src/main/res/drawable/app_ic_tab_*.xml` 단색 벡터(Material Symbols 모양, Apache 2.0). 아이콘 라이브러리 의존성을 늘리지 않으려고 직접 넣었다.

## 계획과 달라진 점 (이유)
- `core:model`·`core:common`과 디자인 토큰(`PauseTheme`)을 0단계 커밋에 먼저 넣었다. 앱 셸과 설정 화면이 토큰을 쓰기 때문이다(1단계 범위를 앞당긴 것뿐, 내용 변경 없음).

## 남은 TODO (파일·이유)
1. `core/data/.../profile/ProfileRepository.kt`, `feature/onboarding/...` — 온보딩 완료 표시만 저장. 2단계에서 실제 프로필 저장·6단계 흐름으로.
2. `feature/{report,coach,friends}/...Screen.kt`, `feature/settings/SettingsScreen.kt` — 빈 화면/일부 항목. 4·5단계에서 채운다.
3. 원격 CI(`.github/workflows/android-ci.yml`) 통과는 PR을 올린 뒤 확인해야 한다.

## 테스트 결과 (모듈별 개수, 실패 0인지)
- `core:common` 4개(DayBoundary) — 실패 0

## AI 평가 요약 (제공자, 스키마·검증 통과율, 금지 표현·숫자 위반, 지연)
- 0단계 스텁: mock, 항목 0(빈 보고서). 4단계에서 실제 평가.

## 실기기 확인 (기기·OS, 확인한 패키지명, 임계값 오차)
- 아직 없음(2·3단계).
