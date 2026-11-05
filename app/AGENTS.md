# app — 모듈 규칙
- 담당: R1 (실제 사람은 .github/CODEOWNERS)
- 하는 일: Application, MainActivity, 내비게이션 3 백 스택·entry provider(`navigation/`), 하단 탭 5개, 딥링크(메시지 알림 → 코치)
- 공개 API: `PauseApp`, `MainActivity`
- 의존 가능: 모든 feature, core
- 금지: 기능 로직을 여기에 두지 않는다. feature끼리 연결만
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
