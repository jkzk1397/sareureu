# :feature:settings — 모듈 규칙
- 담당: R1 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 맥락별 임계값, 예약 알림, 추적 앱, 감지 방식, AI 제공자·감사 로그, 개인정보(내보내기·삭제), 테마, 시연 모드
- 공개 API: 경로 키 `SettingsKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `settingsEntries()` 하나
- 의존 가능: designsystem, data, intervention, ai, social
- 금지: 모든 기록 삭제는 로컬+원격 모두
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
