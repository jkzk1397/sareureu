# ADR-0007 버전과 빌드 도구 (2026-11 기준)

- 상태: 확정 (2026-11-05)
- 결정: `docs/03_ARCHITECTURE.md` 6절 표의 값을 쓴다. 핵심은 AGP 9.5.0(내장 Kotlin), Kotlin 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, **Navigation 3 1.2.1**, Room 2.8.5, Hilt 2.60.1, Firebase BoM 35.1.0. SDK는 minSdk 29 / targetSdk 36 / compileSdk 37. Gradle은 JDK 21로 돌리고 바이트코드는 17로 맞춘다.
- 이유:
  - targetSdk 36은 2026-08-31부터 Google Play 신규 앱·업데이트의 최소값이다. 37(Android 17)을 타깃으로 하면 새 동작 변경을 따로 검증해야 해서 캡스톤 일정에 맞지 않는다.
  - Navigation Compose 2.x는 릴리스 노트에서 유지보수 모드라고 밝혔다. Navigation 3은 1.2에서 안정판이다. 공식 문서 개요에는 아직 Navigation Compose 권장 문구가 남아 있어 판단이 갈린다는 점을 기록해 둔다.
  - Room 3(3.0)은 안정판이지만 드라이버 필수, DAO 전부 suspend/Flow 등 바뀐 점이 많고 예제가 적다. AI 에이전트가 틀리기 쉬워 Room 2.8을 쓴다.
  - AGP 9부터 Kotlin이 내장이라 `org.jetbrains.kotlin.android`를 적용하면 안 된다. kapt는 쓸 수 없고 KSP만 쓴다.
  - Robolectric은 SDK 36 이상에서 JDK 21을 요구한다.
- 결과: Gradle wrapper는 키트에 9.8.2로 들어 있다. 실제로 쓴 값이 표와 다르면 이 문서 아래 "실제 값"에 적는다. 라이브러리 하나가 해결되지 않으면 그 하나만 바로 아래 안정판으로 내리고 여기에 적는다.

## 실제 값 (원샷 실행 후 R1이 채움)
| 항목 | 표 | 실제 | 이유 |
|---|---|---|---|
| JDK(Gradle 실행) | 21 | 25 (Android Studio 내장 JBR, 로컬 PC) | 설치된 JDK가 JBR 25뿐. 바이트코드 17, 빌드·테스트 통과. CI는 temurin 21 그대로 |
| 나머지 | 6절 표 | 6절 표와 같음 | — |
