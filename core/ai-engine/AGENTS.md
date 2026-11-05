# :core:ai-engine — 모듈 규칙
- 담당: R4 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 순수 JVM. LlmClient 인터페이스, MockLlmClient, RestGeminiClient(평가 전용), PromptRegistry(resources/prompts), 출력 스키마, 검증기, SafetyFilter, BannedWords, CoachEngine(도구 호출 루프), 평가 하네스(aiEval)
- 공개 API: `LlmClient`, `PromptRegistry`, `*Validator`, `SafetyFilter`, `CoachEngine`, `CoachTools`(인터페이스)
- 의존 가능: insights, model, common (안드로이드 의존 금지)
- 금지: 기존 프롬프트 파일 수정(새 버전 추가), Kotlin 코드에 프롬프트 문자열, 앱에서 RestGeminiClient 사용, 검증 없이 출력 반환
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
