# :core:insights — 모듈 규칙
- 담당: R4 (실제 사람은 .github/CODEOWNERS)
- 하는 일: 순수 계산: ClockBins, Baseline, ReductionRate, Coverage, RiskWindows, ViewerTypeClassifier, WeeklyObservations, MeltStageCalculator
- 공개 API: 계산 함수들
- 의존 가능: model, common
- 금지: 안드로이드 의존 금지. 모든 함수 단위 테스트
- 루트 AGENTS.md가 우선한다. 이 파일과 충돌하면 루트를 따른다.
