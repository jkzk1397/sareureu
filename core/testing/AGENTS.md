# :core:testing — 모듈 규칙
- 담당: R1 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 테스트용 Fake 저장소·FakeClock·고정 시나리오
- 공개 API: `Fake*`, `TestClock`
- 의존 가능: 모든 core
- 금지: 프로덕션 코드에서 의존 금지
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
