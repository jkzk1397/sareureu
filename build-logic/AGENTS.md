# build-logic — 모듈 규칙
- 담당: R1 (실제 사람은 .github/CODEOWNERS)
- 하는 일: Gradle 컨벤션 플러그인(pause.android.application, pause.android.library, pause.android.library.compose, pause.android.feature, pause.android.hilt, pause.jvm.library)
- 공개 API: 플러그인 ID
- 의존 가능: —
- 금지: 버전 하드코딩 금지(libs.versions.toml만)
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
