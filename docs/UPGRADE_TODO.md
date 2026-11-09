# 고도화 남은 작업 (2026-11-09 정리)

> **커서에서 쓰는 법**: `@docs/UPGRADE_TODO.md 작업 1 진행해줘`처럼 한 번에 하나씩 맡긴다. 커서 에이전트는 `.cursor/rules`를 거쳐 `AGENTS.md`를 먼저 읽는다.
> 작업 하나 = 브랜치 하나 = PR 하나. 끝나면 `./gradlew spotlessApply` → `./gradlew check` → 폰에서 "폰에서 확인" 항목을 본다.
> 끝난 작업은 제목 앞에 ✅를 붙인다. **전부 끝나면 이 파일을 지운다.**
> 권하는 순서: 작업 1 → 2 → 4 → 3. 작업 5는 콘솔 작업이 있어 따로, 작업 6은 서버 담당 팀원 몫, 작업 7은 선택.

## 이미 끝난 것 (PR #12 `fix/real-use-core`, CI 통과)

- 버블 링은 '이번 구간'(처음 기준 시간 또는 연장 5분)만 보여 준다. 5분만 더, 닫기, 오늘은 그만 뒤에 0부터 다시 찬다
- 버블을 누르면 미니 패널: 몇 분째인지, 다음 카드까지 몇 분인지, 바로 '그만 볼래'(0단계 스스로 멈춤으로 기록)
- 버블 표정: 보통 WATCHING, 카드 직전(구간 80%) CALLING, 오늘은 그만이면 SLEEPY
- 오늘 탭: 저장 전 진행 중 세션도 바로 합계에 들어간다(`LiveSessionSource`), 30초마다 다시 센다
- 오늘 탭: 감지가 꺼져 있으면 '감지 켜기' 버튼(`MonitoringSwitch`). 권한 설정에서 돌아오면 다시 시도
- 온보딩 끝에 사용 기록 권한이 있으면 감지를 바로 켜고, 앱을 열 때 멈춘 감지를 다시 켠다
- 실제 사용자에게 시연 모드를 권하던 오늘 탭 문구 삭제

---

## 작업 3. 온보딩 12단계를 5단계로

**왜**: 지금 연령, 약관, 프로필, 하루 리듬(칸 9개), 목표, 추적 앱, 권한 3개 따로, AI, 배터리, 완료 순서라 깔고 나서 지친다. 필수(나이 확인, 약관, 권한 고지, AI 국외 이전 고지)는 남기고 긴 글은 접는다.

**목표 흐름**
1. **시작**: 연령대 고르기(만 18세 미만을 고르면 지금처럼 안내 화면, ADR-0009) + 약관·개인정보 동의를 한 화면에. 전문은 "자세히"로 접는다
2. **나**: 별명, 신분(칩), 자는 시간(평일 하나, 주말은 +1시간 기본). 기상, 수업/업무, 통학, 식사는 신분별 기본값으로 채운다. 나중에 설정 > 하루 리듬에서 고친다(작업 4)
3. **목표**: 목표 칩 + 하루 목표 분. 관심사, 말투는 기본값(설정에서 바꿈)
4. **권한**: 사용 기록(필수), 알림, 다른 앱 위 표시를 한 화면에 세 줄로. 줄마다 한 문장 + [허용] + 허용됨 표시. 고지 전문은 "자세히". 화면으로 돌아오면 상태를 다시 읽는다(지금 `RefreshPermissions`)
5. **AI**: "AI 친구 쓰기 / 나중에". 국외 이전(구글 서버) 한 줄 + "자세히". 동의 기록(`ConsentItem.AI_USE`)은 그대로
→ 시작하기: 프로필 저장 + 감지 켜기(PR #12에 이미 있음)

- 추적 앱(APPS)은 기본값(유튜브, 인스타, 틱톡)으로 두고 설정 > 추적 앱에만 둔다
- 배터리(BATTERY)는 설정 > 감지에 "배터리 최적화 끄기" 줄로 옮긴다
- 화면마다 처음 보이는 글은 두 문장 이하
- 동의 항목 종류는 바꾸지 않는다. 고지 내용이 04 문서와 같으면 `NOTICE_VERSION` 그대로, 내용이 바뀌면 올린다

**파일**: `feature/onboarding/` 전체(`OnboardingState.kt`의 단계 enum과 `SECTIONS = 5`, `OnboardingScreen.kt`, `Steps*.kt`, `strings.xml`). 신분별 기본 리듬 표는 순수 함수로 만들고 테스트한다

**테스트**: `OnboardingFlow` 다음/이전(5단계, 미성년 분기), 신분별 기본 리듬이 `toProfile`에 들어가는지

**폰에서 확인**: 새로 설치 → 5화면 안에 끝. 권한 화면에서 셋 다 허용하고 돌아오면 체크 표시. 시작하기 → 상태 알림(감지 중)

**선행**: 작업 4(리듬 편집기를 공용으로 옮김)를 먼저 하면 편하다

---

## 작업 4. 설정에 '하루 리듬' 편집

**왜**: 온보딩에서 리듬 칸을 빼면 고칠 곳이 있어야 한다. 맥락(기상, 통학, 수업/업무, 식사, 자기 전) 분류가 이 값으로 정해진다.

**할 일**
- `feature/onboarding`의 `RhythmEditor`(순수 계산)와 리듬 입력 UI(`StepsRhythm.kt`)를 `core:designsystem`의 `component/rhythm/`으로 옮겨 온보딩과 설정이 같이 쓴다(feature끼리는 의존 금지, designsystem은 model만 의존하니 가능). 문자열 접두사는 `designsystem_`. R5 승인
- 설정 > "하루 리듬" 화면. 경로 키는 `feature/settings/.../navigation/`, 백 스택 연결은 `app` 내비(R1). 저장은 `ProfileRepository.save`
- 화면 위에 한 줄: "이 시간으로 기상, 통학, 수업, 식사, 자기 전을 나눠"

**폰에서 확인**: 설정 > 하루 리듬에서 자는 시간을 바꾸면 그 뒤 세션의 맥락이 바뀐다(설정 > 개발자 > 최근 세션)

---

## 작업 5. 실제 AI를 모든 폰에서 (Firebase, 콘솔 작업 포함)

지금 CI APK는 `google-services.json`이 없어서 항상 모의 AI다. `app/build.gradle.kts`는 그 파일이 있을 때만 Firebase 플러그인을 켜므로 빌드 쪽 코드는 그대로 된다.

1. **프로젝트**: 서버 팀원이 쓰는 Firebase 프로젝트(`server/wrangler.toml`의 `FIREBASE_PROJECT_ID = "sareureu"`)를 같이 쓴다. 요금제는 **Spark(무료) 그대로**. Blaze로 올리거나 결제 정보를 넣지 않는다
2. Android 앱 추가: 패키지 `com.capstone.pause` → `google-services.json`을 `app/`에 둔다(.gitignore에 있음, 커밋 금지)
3. Firebase AI Logic 켜기 → **Gemini Developer API**(무료 등급). 모델은 `AiConfig.MODEL`
4. **App Check**: 2026-11-02부터 AI Logic은 App Check 강제가 기본이다(2027-01-21까지만 미룰 수 있다)
   - 디버그 빌드는 디버그 공급자를 쓴다. 기본 동작은 폰마다 로그캣에 찍힌 토큰을 콘솔(App Check > 앱 > 디버그 토큰 관리)에 등록해야 한다
   - **모든 폰용 권장안: 공유 디버그 비밀 하나**
     - UUID 하나를 만들어 콘솔 디버그 토큰에 등록하고, 같은 값을 `local.properties`의 `appCheckDebugSecret`에 둔다(커밋 금지)
     - `core/ai/build.gradle.kts` debug에만 `buildConfigField("String", "APP_CHECK_DEBUG_SECRET", ...)` (`buildConfig = true`)
     - `core/ai/src/debug/.../AppCheckSetup.kt`에서 공급자를 설치하기 **전에** 디버그 SDK가 비밀을 읽는 SharedPreferences에 그 값을 써 둔다. 이름은 `com.google.firebase.appcheck.debug.store.<FirebaseApp.getInstance().persistenceKey>`, 키는 `com.google.firebase.appcheck.debug.DEBUG_SECRET`으로 알고 있는데, SDK 내부 방식이라 firebase-android-sdk의 `StorageHelper`에서 한 번 확인하고 쓴다. Firebase BoM을 올릴 때도 다시 확인
     - 이 APK에는 비밀이 들어가니 **공개 CI 아티팩트로 만들지 않는다**(공개 저장소라 누구나 받는다). 로컬에서 `./gradlew assembleDebug`로 만들어 팀과 교수님께 비공개로 전달한다. CI APK는 지금처럼 모의 AI
   - 급할 때만: 콘솔에서 강제 적용을 2027-01-21까지 미룬다. 권장하지 않는다(키가 새면 남이 무료 한도를 쓸 수 있다. 결제 정보가 없으니 요금은 나오지 않는다)
   - release 빌드는 Play Integrity라 직접 설치한 APK에서는 실패하고 모의로 대체된다(앱은 죽지 않는다)
5. **확인**: 설정 > AI 감사 로그에서 제공자 CLOUD, 결과 PASSED. 리포트 탭에서 AI 리포트가 모의 표시 없이 나온다
6. **무료 한도**: 리포트는 하루 1회 자동 + 재생성 제한(`quota`), 코치는 앱이 화면에 있을 때만 부른다. 한도를 넘으면 모의로 대체된다

참고: https://firebase.google.com/docs/ai-logic/app-check

---

## 작업 6. 친구 서버 연동 (서버 담당 팀원)

`server/`(PR #11)는 일일 합계 수집까지 있다. 앱 쪽 할 일(`server/README.md`의 앱 연결 계약):
- Firebase 로그인(익명 로그인이면 충분) → ID 토큰. `firebase-auth` 의존성이 필요한데 `libs.versions.toml`은 R1만 고친다 → 이슈로 요청
- 랭킹 공유 동의 화면 + `PUT /api/v1/sharing`, 철회하면 `DELETE /api/v1/sharing`
- 하루 합계 업로드(WorkManager, 실제 데이터만, 시연 데이터는 보내지 않음)
- 사용자 기록을 원격으로 보내는 코드라 **동의 확인이 먼저**다(AGENTS.md 11절). 인터넷 권한은 지금 Firebase 라이브러리 Manifest에서 병합돼 들어온다. 모듈 Manifest에 직접 적어야 하면 3절 규칙 11(새 권한은 ADR)부터 확인
- 지금 `core:social`, `feature:friends`는 시연용이다. 연동 뒤 친구 탭 문구(`friends_*`)를 정리한다

---

## 작업 7. AI 문구 다듬기 (선택, 프롬프트 v3)

AI 검증 과제(2026-11-09, 실제 Gemini 8회)에서 남은 것:
- `{buddy}`를 폰처럼 쓴다("{buddy} 내려놓기")
- "하루 평균 {avgMin}만큼 가벼워졌어"처럼 평균을 줄어든 양처럼 쓴다
- 리포트에 '녹았다' 이야기를 지어낸다(녹는 단계는 입력에 없다)
- 근거 없는 이유를 지어낸다("시험 끝나서 해방감")

할 일: `core/ai-engine/src/main/resources/prompts/weekly_report_v3.md` 새 파일(v2는 고치지 않음) + `PromptRegistry` 현재 버전 3 + 검증기에 패턴 추가 + 미끼 테스트 + `./gradlew :core:ai-engine:aiEval -Pprovider=mock` 결과를 PR에 첨부
