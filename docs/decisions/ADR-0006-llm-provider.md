# ADR-0006 LLM 제공자: Firebase AI Logic + 모의, 기기 안 모델은 선택

- 상태: 확정 (2026-11-03)
- 결정: 앱의 클라우드 LLM 호출은 Firebase AI Logic(Gemini Developer API 백엔드)으로만 한다. API 키를 APK에 넣지 않고 App Check(Play Integrity, 디버그는 디버그 공급자)로 보호한다. 키가 없거나 실패하면 `MockLlmClient`. 기기 안 Gemini Nano(ML Kit GenAI Prompt API)는 지원 기기에서 짧은 작업에만 선택적으로 쓴다. 평가 하네스(JVM)만 REST + 로컬 `GEMINI_API_KEY`를 쓴다.
- 이유: 앱에 키를 넣으면 추출된다. Firebase AI Logic은 무료 요금제에서도 Gemini Developer API를 쓸 수 있고 클라이언트 SDK가 있다. 기기 안 모델은 지원 기기가 적고 포그라운드에서만 돈다.
- 결과: 모델 ID는 `AiConfig`(Remote Config로 바꿀 수 있게) 한 곳에. 무료 등급은 입력이 서비스 개선에 쓰일 수 있으므로 동의 화면에 고지.
- 보완 (2026-11-05): 기본 모델 `gemini-3.5-flash-lite`(안정판, 종료 2027-07-21 이후), 선택 `gemini-3.8-flash`. 2.0(2026-06-01 종료), 2.5(신규 프로젝트 불가), 3.1-flash-lite(2027-05-07 종료)는 쓰지 않는다. **2026-11-02부터 Firebase AI Logic은 App Check 적용이 필수**이고, 제한 사용 토큰(`useLimitedUseAppCheckTokens = true`)을 쓴다. Gemini API 약관상 만 18세 이상만 쓸 수 있어 앱 전체를 18세 이상으로 한다(ADR-0009). 출처 `docs/research/policy.md` 6절.
