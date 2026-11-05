# :feature:coach — 모듈 규칙
- 담당: R4 (실제 사람은 .github/CODEOWNERS)
- 하는 일: AI 친구 채팅(근거 칩, 추천 질문, 계획으로 저장), 르르 메시지 스레드
- 공개 API: 경로 키 `CoachKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `coachEntries()` 하나
- 의존 가능: designsystem, data, ai
- 금지: 계획 자동 저장 금지(버튼으로만)
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
