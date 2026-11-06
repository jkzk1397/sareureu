# 팀원 첫날 가이드

> 처음 합류한 팀원이 자기 PC에서 앱을 빌드·실행하고, 규칙대로 PR을 올리기까지. 막히면 "자주 막히는 곳".

## 0. 지금 상태와 남은 일
앱은 **시연 모드 기준으로 거의 완성**됐다(모든 화면·개입 사다리·모의 AI가 돈다). 남은 일은 이슈로 나눠 맡는다. 자세한 목록은 `docs/BUILD_REPORT.md` "남은 TODO"·"사람이 할 확인".
- **실폰 확인**: 유튜브·인스타·틱톡을 볼 때 감지·카드·알림이 제대로 뜨는지(특히 삼성 절전·잠금)
- **Firebase 연결**: 실제 AI(Gemini, README "클라우드 AI 붙이기"), 친구 기능 서버(`core:social`)
- **기록률 저장**: 감지가 살아 있던 구간 기록(랭킹 조작 방지, ADR-0005)
- **화면 다듬기·발표 준비**: README "시연 수용 테스트" 7단계

## 1. 설치 (한 번만, 약 30분)
1. **Android Studio** 최신 안정판(Rabbit 2 이상). 처음 실행할 때 기본 설치를 고르고, SDK Manager에서 **SDK Platforms › Android 17 (API 37)** 과 **SDK Tools › Build-Tools · Platform-Tools · Emulator** 를 체크한다.
2. **Git**(git-scm.com). 윈도우는 Git for Windows.
3. (윈도우) 환경 변수 — PowerShell에서 한 번 실행하고 터미널을 새로 연다:
   ```powershell
   [Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
   [Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
   ```
   macOS는 Android Studio가 알아서 잡는다. JDK를 따로 설치할 필요는 없다(Android Studio 안의 JBR을 쓴다).
4. `git config --global user.name "이름"` · `git config --global user.email "깃허브 이메일"`. 리드에게 GitHub 아이디를 알려 저장소 협업자로 초대받는다.

## 2. 받기와 첫 실행 (약 15분, 첫 Gradle 동기화가 오래 걸림)
1. 경로에 **한글·공백이 없는** 폴더에서:
   ```bash
   git clone https://github.com/jkzk1397/sareureu.git capstoneproject
   ```
2. Android Studio › Open › `capstoneproject` 폴더. 오른쪽 아래 Gradle 동기화가 끝날 때까지 기다린다(처음 5~15분). `local.properties`는 Android Studio가 만든다(커밋하지 않는다).
3. 실행할 기기:
   - **에뮬레이터**: Device Manager › Create Virtual Device › Pixel 계열 · Android 16 또는 17. 램이 16GB 미만인 PC는 에뮬레이터가 무거우니 실폰을 권한다.
   - **실폰**: 설정 › 휴대전화 정보 › 빌드 번호 7번 탭 → 개발자 옵션 › USB 디버깅 켜기 → USB 연결 → "이 컴퓨터 허용".
4. 위쪽 ▶(Run 'app'). 앱이 뜨면 온보딩 → **설정 › 시연 › 시연 모드 켜기** → 모든 탭에 가상 기록이 보인다.
   - 화면 위 캐릭터·카드까지 보려면 폰 설정에서 이 앱의 '다른 앱 위에 표시'를 허용한다.
5. 명령줄로 하고 싶으면(같은 결과):
   ```bash
   ./gradlew installDebug        # 윈도우 PowerShell은 .\gradlew.bat installDebug
   ```

## 3. 폴더 구성 — 어디를 고치나
```
capstoneproject/
├─ app/                  앱 시작점: MainActivity, 하단 탭, 화면 이동(navigation/)
├─ feature/              화면 하나 = 모듈 하나 (서로 의존하지 않음)
│  ├─ onboarding/        첫 실행: 프로필·하루 리듬·권한 고지
│  ├─ today/  report/  coach/  friends/  settings/   하단 탭 5개
├─ core/                 화면이 아닌 공용 부품
│  ├─ model/             도메인 타입(계약, 순수 Kotlin)
│  ├─ common/            Clock·디스패처·로그
│  ├─ designsystem/      색·글꼴·간격 토큰, 공용 컴포넌트, 르르 캐릭터
│  ├─ database/  data/   Room DB / 저장소·설정·시연 데이터
│  ├─ sensing/           사용 기록 감지, 세션·맥락·경로 분류
│  ├─ insights/          통계 계산(순수 Kotlin, 숫자는 여기서만 센다)
│  ├─ intervention/      화면 위 르르·카드, 알림, '5분만 더' 사다리
│  ├─ ai-engine/  ai/    프롬프트·검증기·모의 AI / Firebase AI 연결
│  └─ social/            친구·랭킹·잔소리
├─ build-logic/          Gradle 공통 설정(컨벤션 플러그인)
├─ gradle/libs.versions.toml   라이브러리 버전(리드만 수정)
└─ docs/                 기획(01)·구조(03)·데이터(04)·AI(05)·디자인(06), 결정 기록(decisions/ADR-*)
```
- **모듈 하나의 안**(예: `feature/today/`): `AGENTS.md`(이 모듈 규칙 10줄) · `src/main/kotlin/com/capstone/pause/feature/today/`(`TodayScreen.kt` 화면, `TodayViewModel.kt` 상태, `navigation/` 경로) · `src/main/res/values/strings.xml`(문구) · `src/test/`(테스트).
- **흐름**: 화면(Screen) → ViewModel → `core:data` 저장소 → DB·감지. 계산은 `core:insights`, AI 문장은 `core:ai`.
- **찾는 법**: 고치고 싶은 게 화면이면 `feature/<탭>/`, 글자면 그 모듈 `strings.xml`, 색·간격이면 `core/designsystem/`, 카드·알림이면 `core/intervention/`, 숫자가 이상하면 `core/insights/`. 누가 맡는지는 `AGENTS.md` 4절 표.

## 4. 일하는 방법 (규칙 원본은 `AGENTS.md`)
1. 이슈 하나를 맡는다(GitHub Issues). 맡은 모듈은 `AGENTS.md` 4절 표, 실제 담당자는 `.github/CODEOWNERS`.
2. 시작할 때마다:
   ```bash
   git checkout main && git pull --rebase origin main
   git checkout -b feat/<모듈>-<설명>     # fix/…, docs/…, contract/…
   ```
3. 그 모듈의 `AGENTS.md`를 읽고 그 파일들만 고친다. 문구는 `strings.xml`, 색·간격은 디자인 토큰, 버전 추가는 리드에게 요청.
4. PR 전에 꼭:
   ```bash
   ./gradlew spotlessApply
   ./gradlew check assembleDebug
   ```
5. push → GitHub에서 `main`으로 PR(템플릿 체크리스트 채우기, 한국어). CI(`android-ci`)가 초록이 되고 리뷰를 받으면 **squash merge**. `main`에 직접 push하지 않는다.
6. AI 코딩 도구(Claude Code·Cursor·Gemini CLI·Copilot)를 쓰면 `AGENTS.md`를 자동으로 읽는다. 사람에게 쓰는 말은 한국어.

## 5. Android Studio 없이 확인·디버깅하기
웹 버전은 없다. 이 앱은 안드로이드의 사용 기록·다른 앱 위에 표시·알림을 쓰기 때문에 웹으로는 같은 기능을 보일 수 없고, 따로 만들려면 큰 작업이라 하지 않는다. 대신 이 방법들이 있다.

| 하고 싶은 것 | 방법 | 필요한 것 |
|---|---|---|
| 브라우저에서 앱 실행(설치 없음) | **Appetize.io**(무료 체험 한도 있음) — 아래 app-debug APK를 올리면 링크가 생기고 브라우저에서 폰 화면을 눌러 볼 수 있다. 시연 모드로 모든 화면 확인 가능. 실제 숏폼 앱 위에 뜨는 르르·감지는 실폰에서만 | 브라우저 |
| 빌드 없이 최신 앱을 폰에 깔기 | GitHub › Actions › 해당 PR의 실행 › **Artifacts › app-debug** 내려받기 → 압축 풀고 APK를 폰으로 옮겨 설치('출처를 알 수 없는 앱' 허용). 14일 보관 | 폰만 |
| 로직만 빠르게 확인 | `./gradlew test` — 계산(`core:insights`), 맥락 분류, 경로, 개입 사다리, AI 검증기는 에뮬레이터 없이 JVM에서 돈다 | JDK |
| AI 문장 품질 확인 | `./gradlew :core:ai-engine:aiEval -Pprovider=mock` → `core/ai-engine/build/reports/ai-eval/report.md` | JDK |
| 폰 화면을 PC에 띄워 조작·녹화(시연용) | **scrcpy**(무료 오픈소스) — USB 디버깅 켠 폰을 연결하고 `scrcpy` 실행 | scrcpy 설치 |
| 로그 보기 | `adb logcat | grep -i pause` (Android Studio 없이 SDK Platform-Tools만 있어도 됨) | Platform-Tools |
| 화면 모양만 보기 | Android Studio의 Compose **Preview**(각 Screen 파일의 라이트·다크 미리보기) | Android Studio |

## 6. 자주 막히는 곳
- **Gradle 동기화 실패 / JDK 오류**: Android Studio › Settings › Build Tools › Gradle › Gradle JDK를 "JetBrains Runtime(JBR) 21 이상"으로. 그래도 안 되면 File › Invalidate Caches › Restart.
- **"SDK location not found"**: Android Studio로 한 번 열면 `local.properties`가 생긴다. 명령줄만 쓰면 `local.properties`에 `sdk.dir=C\:\\Users\\<이름>\\AppData\\Local\\Android\\Sdk`.
- **compileSdk 37 없음**: SDK Manager에서 Android 17 (API 37) 설치.
- **경로 문제**: 한글·공백이 있는 경로, 너무 깊은 경로(윈도우 260자 제한)는 피한다(예: `C:\dev\capstoneproject`).
- **에뮬레이터가 5분 뒤 "Timed out … waiting for emulator"**: 강제로 꺼질 때 저장된 빠른 부팅 상태가 망가진 것. Device Manager › 기기 줄 ⋮ › **Cold Boot**. 끌 때는 창의 X나 ■로 정상 종료한다.
- **에뮬레이터가 느림/검은 화면**: Device Manager › 기기 ⋮ › Edit에서 RAM 4096MB, Graphics를 Hardware로. Discord·브라우저 탭 등 큰 프로그램을 닫는다. PC 램이 16GB 미만이면 실폰 사용.
- **`spotlessCheck` 실패**: `./gradlew spotlessApply` 후 다시 커밋.
- **AI가 "모의 AI"라고 나옴**: 정상. 무료로 쓰는 기본 모드다(규칙 기반). 클라우드 AI는 README "클라우드 AI 붙이기".

## 7. 리드가 한 번 할 일
- 저장소 › Settings › Collaborators › Add people로 팀원 초대(비공개 저장소, 무료로 인원 제한 없음).
- `.github/CODEOWNERS`의 `@R1-github-id` 등을 실제 팀원 아이디로 바꾸고 줄 앞 `#`을 지운다.
- `main` 보호 설정은 무료 계정의 비공개 저장소에서는 쓸 수 없다. "main 직접 push 금지, PR + CI 통과 후 squash merge"를 규칙으로 지킨다(AI 도구는 `AGENTS.md`로 지킨다). 비공개 저장소의 Actions는 월 2,000분 무료(CI 한 번 약 8분).
- `docs/BUILD_REPORT.md` "남은 TODO"를 이슈로 옮겨 팀원에게 나눠 준다.
