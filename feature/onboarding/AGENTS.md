# :feature:onboarding — 모듈 규칙
- 담당: R3 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 프로필·하루 리듬·목표·관심사·말투·추적 앱, 권한 고지·요청, AI 사용 동의
- 공개 API: 경로 키 `OnboardingKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `onboardingEntries()` 하나
- 의존 가능: designsystem, data, sensing, intervention
- 금지: 권한마다 고지 화면 + 거절 시 대체 흐름
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
