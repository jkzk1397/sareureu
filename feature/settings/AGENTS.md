# :feature:settings — 모듈 규칙
- 담당: R1 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 접는 묶음 6개 — 알림(기준 평소·밤, 예약 알림, 하루 리듬 편집), 감지(추적 앱, 감지 방식), AI(제공자·감사 로그), 개인정보(내보내기·삭제), 화면(테마), 더 보기(시연 모드, 개발자 메뉴, 정보). 긴 설명·고지는 한 줄 요약 + '자세히'
- 공개 API: 경로 키 `SettingsKey`·`SettingsRhythmKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `settingsEntries(onOpenRhythm, onBack)` 하나
- 의존 가능: designsystem, data, intervention, ai, social
- 금지: 모든 기록 삭제는 로컬+원격 모두
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
