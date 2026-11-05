# :core:sensing — 모듈 규칙
- 담당: R2 (실제 사람은 .github/CODEOWNERS)
- 하는 일: ForegroundSource 3종, SessionTracker, ContextClassifier, PathResolver, DemoScenario, app_kinds.json, surface_rules.json
- 공개 API: `ForegroundSource`, `LiveSessionFlow`, `DemoController`
- 의존 가능: data, model, common
- 금지: 화면 글자 저장 금지. 다른 앱 조작 금지. isAccessibilityTool 선언 금지
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
