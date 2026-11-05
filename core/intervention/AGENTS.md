# :core:intervention — 모듈 규칙
- 담당: R3 (실제 사람은 .github/CODEOWNERS)
- 하는 일: MonitorService, NudgeEngine(상태 기계), OverlayController, 플로팅 르르, 카드 1~4단계, PauseNotifier(MessagingStyle), ReminderScheduler
- 공개 API: `MonitoringController`, `NudgeEngine`
- 의존 가능: sensing, data, designsystem, model, common
- 금지: core:ai 의존 금지. 다른 앱 닫기·조작 금지. '그만'은 홈 인텐트
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
