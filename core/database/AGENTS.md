# :core:database — 모듈 규칙
- 담당: R2 (실제 사람은 .github/CODEOWNERS)
- 하는 일: Room DB `pause.db`, 엔티티, DAO, 마이그레이션, schemas/
- 공개 API: `PauseDatabase`, DAO들
- 의존 가능: model, common
- 금지: 버전 올리면 마이그레이션+테스트+스키마 JSON 같은 PR
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
