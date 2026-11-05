# 팀원 첫날 가이드

> 처음 합류한 팀원이 자기 PC에서 앱을 빌드·실행하고, 규칙대로 PR을 올리기까지. 막히면 맨 아래 "자주 막히는 곳".

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

## 3. 일하는 방법 (규칙 원본은 `AGENTS.md`)
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

## 4. Android Studio 없이 확인·디버깅하기
웹 버전은 없다. 이 앱은 안드로이드의 사용 기록·다른 앱 위에 표시·알림을 쓰기 때문에 웹으로는 같은 기능을 보일 수 없고, 따로 만들려면 큰 작업이라 하지 않는다. 대신 이 방법들이 있다.

| 하고 싶은 것 | 방법 | 필요한 것 |
|---|---|---|
| 빌드 없이 최신 앱을 폰에 깔기 | GitHub › Actions › 해당 PR의 실행 › **Artifacts › app-debug** 내려받기 → 압축 풀고 APK를 폰으로 옮겨 설치('출처를 알 수 없는 앱' 허용). 14일 보관 | 폰만 |
| 로직만 빠르게 확인 | `./gradlew test` — 계산(`core:insights`), 맥락 분류, 경로, 개입 사다리, AI 검증기는 에뮬레이터 없이 JVM에서 돈다 | JDK |
| AI 문장 품질 확인 | `./gradlew :core:ai-engine:aiEval -Pprovider=mock` → `core/ai-engine/build/reports/ai-eval/report.md` | JDK |
| 폰 화면을 PC에 띄워 조작·녹화(시연용) | **scrcpy**(무료 오픈소스) — USB 디버깅 켠 폰을 연결하고 `scrcpy` 실행 | scrcpy 설치 |
| 로그 보기 | `adb logcat | grep -i pause` (Android Studio 없이 SDK Platform-Tools만 있어도 됨) | Platform-Tools |
| 화면 모양만 보기 | Android Studio의 Compose **Preview**(각 Screen 파일의 라이트·다크 미리보기) | Android Studio |

## 5. 자주 막히는 곳
- **Gradle 동기화 실패 / JDK 오류**: Android Studio › Settings › Build Tools › Gradle › Gradle JDK를 "JetBrains Runtime(JBR) 21 이상"으로. 그래도 안 되면 File › Invalidate Caches › Restart.
- **"SDK location not found"**: Android Studio로 한 번 열면 `local.properties`가 생긴다. 명령줄만 쓰면 `local.properties`에 `sdk.dir=C\:\\Users\\<이름>\\AppData\\Local\\Android\\Sdk`.
- **compileSdk 37 없음**: SDK Manager에서 Android 17 (API 37) 설치.
- **경로 문제**: 한글·공백이 있는 경로, 너무 깊은 경로(윈도우 260자 제한)는 피한다(예: `C:\dev\capstoneproject`).
- **에뮬레이터가 느림/검은 화면**: Device Manager에서 Graphics를 Hardware로, 램 부족이면 실폰 사용.
- **`spotlessCheck` 실패**: `./gradlew spotlessApply` 후 다시 커밋.
- **AI가 "모의 AI"라고 나옴**: 정상. 무료로 쓰는 기본 모드다(규칙 기반). 클라우드 AI는 README "클라우드 AI 붙이기".

## 6. 리드가 한 번 할 일
- `.github/CODEOWNERS`의 `@R1-github-id` 등을 실제 팀원 아이디로 바꾸고 줄 앞 `#`을 지운다.
- GitHub 저장소 › Settings › Branches에서 `main` 보호(PR 필수, CI 통과 필수, squash merge만).
- `docs/BUILD_REPORT.md` "남은 TODO"를 이슈로 옮겨 팀원에게 나눠 준다.
