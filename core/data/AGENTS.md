# :core:data — 모듈 규칙
- 담당: R2 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 저장소 인터페이스·구현, DataStore(설정·프로필·동의 기록), 문구 팩 저장소
- 공개 API: `SessionRepository`, `ProfileRepository`, `SettingsRepository`, `CopyPackRepository`, `InsightRepository`
- 의존 가능: database, insights, model, common
- 금지: UI 타입(Compose) 의존 금지
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
