# :feature:onboarding — 모듈 규칙
- 담당: R3 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 5화면 온보딩 — 연령·약관, 별명·신분·자는 시간(신분별 기본 리듬 `DefaultRhythms`), 목표, 권한 고지·요청(한 화면 세 줄), AI 사용 동의
- 공개 API: 경로 키 `OnboardingKey`(`@Serializable`, `NavKey`, `navigation/` 패키지) + Navigation 3 entry provider DSL에 붙는 확장 함수 `onboardingEntries()` 하나
- 의존 가능: designsystem, data, sensing, intervention
- 금지: 권한끼리 한 버튼으로 묶기. 권한마다 자기 고지(한 줄 + 자세히)와 거절 시 대체 흐름이 있어야 한다
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
