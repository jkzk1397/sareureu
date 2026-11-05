# :core:common — 모듈 규칙
- 담당: R1 (실제 사람은 .github/CODEOWNERS)
- 하는 일: Clock 제공, 디스패처 한정자, Result, AppLog, 날짜 경계(새벽 4시) 유틸
- 공개 API: `AppClock`, `@Dispatcher`, `AppLog`, `DayBoundary`
- 의존 가능: model
- 금지: 개인정보를 로그로 남기지 않는다
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
