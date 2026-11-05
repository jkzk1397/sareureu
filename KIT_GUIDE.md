# 키트 사용법 (팀용)

이 폴더는 새 GitHub 저장소의 **첫 커밋**으로 그대로 올리는 시작 키트다. 코드는 아직 없고, AI 코딩 도구가 코드를 만들 때 따를 규칙·명세·목업이 들어 있다.

## 들어 있는 것
| 파일 | 무엇 | 누가 읽나 |
|---|---|---|
| `ONE_SHOT_PROMPT.md` | AI 코딩 도구에 붙여 넣을 원샷 프롬프트 | 리드(R1)가 한 번 실행 |
| `AGENTS.md` | 모든 AI 도구 공통 규칙(원본) | 모든 AI, 모든 팀원 |
| `CLAUDE.md`, `GEMINI.md`, `.gemini/settings.json`, `.github/copilot-instructions.md`, `.cursor/rules/00-agents.mdc` | 도구별로 AGENTS.md를 가리키는 연결 파일 | 각 도구가 자동으로 |
| `docs/01_PRD.md` | 제품 정의, 요청 기능 검토(원안→판정→최종), 범위, 흐름, 지표 | 전원 |
| `docs/02_RESEARCH.md` | 시장·경쟁·근거·정책·기술 조사와 출처 | 발표·보고서 담당 |
| `docs/03_ARCHITECTURE.md` | 시스템 구조, 모듈, 데이터 흐름, 버전 표 | 개발 전원 |
| `docs/04_DATA_PRIVACY.md` | 데이터 모델(계약), 수집·보관·동의 | R2, R6 |
| `docs/05_AI_SPEC.md` | AI 기능 계약, 프롬프트 규칙, 검증, 안전, 평가 | R4 |
| `docs/06_DESIGN_SYSTEM.md` | 색·글꼴·컴포넌트·하루 시계·르르(캐릭터) 명세 | R5 |
| `docs/brand/BRAND.md`, `docs/brand/BRAND_PROMPTS.md` | 이름·말투·캐릭터 규칙, 르르 그림을 더 만들 때 쓸 이미지 생성 프롬프트 | R5, 발표 담당 |
| `docs/07_BUSINESS.md` | 사업 관점: 린 캔버스, 수익 모델, 지표, 로드맵 | 발표 담당 |
| `docs/decisions/` | 확정한 결정(ADR). AI가 다시 뒤집지 못하게 | 전원 |
| `docs/design/mockups/*.png`, `docs/design/sareureu_screen_mockups.pdf` | 화면 목업 12장(라이트·다크)과 PDF 묶음 | R5, AI |
| `docs/design/reference/` | 팀 시안(`team_mockup.png`)과 르르 원본 그림(`ruru_master.png`) | R5 |
| `docs/brand/logo/` | 확정 로고(워드마크) SVG·PNG, 라이트·다크 | R5, 발표 담당 |
| `core/designsystem/src/main/res/` | 글꼴(`pause_sans_variable.ttf`), 르르 몸(`drawable-nodpi/designsystem_buddy_body.webp`), 알림 실루엣(`drawable/designsystem_ic_buddy_silhouette.xml`), 로고(`drawable/designsystem_wordmark.xml`) | R5 |
| `docs/PROGRESS.md` | 단계별 체크리스트 | 전원 |
| `.github/` | PR·이슈 템플릿, CODEOWNERS, CI | 전원 |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/` | 빌드 도구(Gradle 9.8.2를 처음 빌드할 때 자동으로 받는다). 고치지 않는다 | — |

## 시작 순서 (윈도우, `C:\dev\capstoneproject` 기준)
이 팀의 기본 환경은 **윈도우 + Cursor + Claude Code + Android Studio**다. SDK·에뮬레이터는 Android Studio 것 하나만 쓴다. 빌드 도구 파일(`gradlew`, `gradlew.bat`, `gradle/wrapper/`)은 저장소에 이미 들어 있다.

### 0. 한 번만 하는 준비 (팀원 각자)
1. **Android Studio**(Rabbit 2 이상) → SDK Manager에서 SDK Platforms의 **Android 17 (API 37)**, SDK Tools의 Build-Tools·Platform-Tools·Emulator를 체크한다. 에뮬레이터를 쓰면 Device Manager에서 가상 기기 하나(Pixel, Android 16 또는 17)를 만든다.
2. **Git for Windows**(git-scm.com)를 설치한다. Claude Code가 이것의 bash로 명령을 돌린다.
3. **환경 변수**: PowerShell에서 한 번 실행하고 터미널을 새로 연다(Android Studio에 들어 있는 JDK와 SDK를 가리킨다).
   ```powershell
   [Environment]::SetEnvironmentVariable("JAVA_HOME", "C:\Program Files\Android\Android Studio\jbr", "User")
   [Environment]::SetEnvironmentVariable("ANDROID_HOME", "$env:LOCALAPPDATA\Android\Sdk", "User")
   ```
   새 터미널에서 `& "$env:JAVA_HOME\bin\java" -version`이 21이면 된다.
4. **Claude Code**: Cursor 터미널에서 `claude --version`을 쳐 본다. 버전이 나오면 끝. 안 나오면 PowerShell에서 `irm https://claude.ai/install.ps1 | iex`로 설치한다.
5. **GitHub**: `git config --global user.name "이름"`, `git config --global user.email "깃허브 이메일"`. 처음 push할 때 브라우저 로그인 창이 뜨면 로그인한다(Git for Windows의 자격 증명 관리자).

### 1. 저장소 받기
- `C:\dev\capstoneproject`가 **비어 있어야** 한다(안에 파일이 있으면 다른 곳으로 옮긴다).
  ```powershell
  cd C:\dev
  git clone https://github.com/<계정>/<저장소>.git capstoneproject
  ```
- Cursor에서 File → Open Folder → `C:\dev\capstoneproject`.

### 2. 원샷 실행 (리드 한 명)
Cursor 터미널에서:
```powershell
git checkout -b chore/bootstrap
claude
```
> ONE_SHOT_PROMPT.md를 읽고 0~1단계까지만 실행해

끝나면 `git push -u origin chore/bootstrap` → GitHub에서 PR → `main`에 머지. 이제 팀원이 합류한다.

### 3. 앱 실행해 보기
Android Studio에서 File → Open → `C:\dev\capstoneproject`(같은 폴더) → 에뮬레이터나 USB 디버깅을 켠 폰을 고르고 ▶. Claude에게 "폰에 설치해 줘"라고 하면 `./gradlew installDebug`를 쓴다. Compose 미리보기·Logcat도 Android Studio에서 본다. **코드는 Cursor의 Claude 한 곳에서만 고친다**(Android Studio의 Gemini 에이전트를 같은 때 쓰지 않는다).

### 4. 그 뒤 매일 (전원)
```powershell
git checkout main; git pull --rebase
git checkout -b feat/sensing-usage-source
claude
```
> AGENTS.md와 core/sensing/AGENTS.md를 먼저 읽고, 이슈 #12 범위만 작업해

끝나면 `./gradlew spotlessApply check`(Claude가 돌린다) → push → PR → 리뷰 1명 → squash merge. 로컬 빌드가 어려운 팀원은 PR마다 GitHub Actions가 만드는 `app-debug` APK를 받아 폰에 설치해 본다.

### 그 밖에
- **역할 나누기**: AGENTS.md 4절의 R1~R6을 배정하고 `.github/CODEOWNERS`의 아이디를 채운 뒤 주석을 푼다. `main` 브랜치 보호(직접 push 금지, PR 리뷰 1명, CI 통과 필수)를 켠다.
- **이름**: 앱 **사르르**, 캐릭터 **르르**. 공모전·출시 전 KIPRIS 상표 검색(`docs/brand/BRAND.md` 5절).
- **Firebase**(선택): 친구 기능을 붙일 때 R6이 프로젝트를 만들고 `google-services.json`은 각자 로컬에만 둔다(커밋 금지).
- 프로젝트 경로는 짧게(`C:\dev\...`) 둔다. 윈도우의 긴 경로 문제를 피한다.

## 충돌을 줄이는 습관
- 한 이슈 = 한 브랜치 = 한 AI 세션 = 한 PR.
- AI에게 일을 시킬 때 첫 문장은 항상 "AGENTS.md와 <모듈>/AGENTS.md를 먼저 읽고, 이슈 #번호 범위만 작업해."
- `libs.versions.toml`, `settings.gradle.kts`, 앱 Manifest, 내비 경로, Room 스키마, 디자인 토큰, 프롬프트 파일은 담당자만 고친다.
- 계약(타입·인터페이스·스키마)을 바꿔야 하면 그것만 담은 작은 PR을 먼저 머지한다.
- 매일 아침 `git pull --rebase`, PR 올리기 전에 한 번 더.

## 맥을 쓰는 팀원
위 순서와 같다. 환경 변수는 `~/.zshrc`에 `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`, `export ANDROID_HOME="$HOME/Library/Android/sdk"`.
