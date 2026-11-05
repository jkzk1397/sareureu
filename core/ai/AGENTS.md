# :core:ai — 모듈 규칙
- 담당: R4 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 안드로이드. CloudLlmClient(Firebase AI Logic, App Check), OnDeviceLlmClient(ML Kit GenAI, 지원 기기만), AiRouter, AiCallLogger(Room), WeeklyReportGenerator, CopyPackGenerator, PokeDrafter, CoachTools 구현(저장소 조회)
- 공개 API: `AiRouter`, `WeeklyReportGenerator`, `CopyPackGenerator`, `CoachSession`, `PokeDrafter`
- 의존 가능: ai-engine, data, insights, model, common
- 금지: 백그라운드 호출(앱이 화면에 있을 때만), 동의 없이 클라우드 호출, google-services.json 없을 때 크래시(→ Mock으로), API 키를 앱에 넣기
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
