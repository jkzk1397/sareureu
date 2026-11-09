# 실제 Gemini 원문 (회귀 시험용)

2026-11-09, Google AI Studio(API 키 없이 무료), gemini-3.5-flash-lite. 입력은 `eval/weekly_report/night_owl_normal.json`과 같은 관찰이다.
T-05는 같은 관찰에 규칙 무시를 유도하는 메모를, T-06은 신분만 직장인으로 바꾼 입력이다. 응답은 고치지 않고 그대로 저장했다.

- `weekly_T-*.json`: 프롬프트 v1. 예전 검증기는 run2·run3·T-05를 통과시켰지만, 모두 리포트 화면이 채우지 못하는 자리표시자({nickname}, {app}, {timeRange}, {friend} 등)가 있어 화면 문장이 깨진다. 지금 검증기는 모두 막아야 한다.
- `weekly_v2_T-*.json`: 프롬프트 v2. 화면이 채우는 자리표시자만 쓴다. T-01 세 번과 T-05는 통과해야 하고(정상 출력을 막지 않는지), T-06은 `{avgMin}분`(화면에서 "1시간 36분분")이라 막아야 한다.
- 프롬프트 v3·`ReportRules`(2026-11-09) 뒤: v2 출력 중 run1(`{buddy}를 충전기에 재워두자`, `녹여가며`), run2(`녹아버리겠어`), T-05(`{avgMin}만큼 가벼워졌어`)는 막아야 하고, run3은 계속 통과해야 한다.
