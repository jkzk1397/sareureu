# Android 플랫폼 / Google Play 정책 / AI / 법률 제약 조사 (숏폼 시청 줄이기 앱)

- 조사일: 2026-11-05 (WebSearch/WebFetch로 공식 문서 직접 확인)
- 방법 한계: developer.android.com의 API reference 페이지(UsageStatsManager, UsageEvents.Event 등)는 내비게이션만 추출되어 본문을 직접 읽지 못함. 해당 항목은 AOSP 소스(android.googlesource.com), API diff 페이지, Microsoft .NET for Android 미러(구글 원문 javadoc 인용)로 교차 확인함. 출처마다 표시.
- 인용문은 원문(영어) 그대로, 해석은 한국어.

---

## 1. Foreground Service(FGS) 타입과 제약

### 결론: `specialUse` 사용 (dataSync 아님)

- **specialUse가 맞는 타입**: 공식 정의는 "Covers any valid foreground service use cases that aren't covered by the other foreground service types." 매니페스트에 `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>`로 사용 사례를 적어야 하며, "These values and corresponding use cases are reviewed when you submit your app in the Google Play Console." 권한은 `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`. 런타임 선행조건은 없음.
  - 출처: https://developer.android.com/develop/background-work/services/fgs/service-types
  - 확신도: 높음
- **dataSync가 안 맞는 이유**: dataSync 정의는 "Data transfer operations"(업로드/다운로드, 백업·복원, import/export, fetch, 로컬 파일 처리, 클라우드 전송). 사용 시간 모니터링과 맞지 않음. 또 Android 15(target 35)부터 24시간 중 6시간 제한이 있고 BOOT_COMPLETED에서 시작할 수 없음(아래 참고). Play의 dataSync 사용 사례 표도 "user-initiated backup/restore, upload/download, import/export"뿐임.
  - 출처: 위 service-types 페이지, https://support.google.com/googleplay/android-developer/answer/13392821?hl=en
  - 확신도: 높음
- **systemExempted는 대상 아님**: "Reserved for system applications and specific system integrations"(Device Owner, Device Admin, VPN, SCHEDULE_EXACT_ALARM/USE_EXACT_ALARM 보유 등). 조건이 안 되면 `ForegroundServiceTypeNotAllowedException`이 남. 이 타입 하나 때문에 exact alarm 권한을 넣지 말 것(우리 앱은 exact alarm을 쓰지 않음).
  - 출처: 위 service-types 페이지 / 확신도: 높음
- Play 사용 사례 표에는 "digital wellbeing / screen time / app usage monitoring" 항목이 없음. 목록은 "non-exhaustive"이며 "if you do not see your use case listed, you can enter your use case manually."
  - 출처: https://support.google.com/googleplay/android-developer/answer/16965181?hl=en / 확신도: 높음

### Play Console 선언 요구사항

- 경로는 Policy > App content > Foreground service permissions. 타입마다 다음을 제출함:
  1. 해당 타입을 쓰는 기능 설명
  2. 작업이 지연되거나 중단될 때 사용자에게 미치는 영향
  3. 동영상 링크: "The video should demonstrate the steps the user needs to take in your app in order to trigger the feature."
  4. 사용 사례: 목록에서 고르거나 직접 입력
- specialUse는 "limited scenarios"에서만 허용됨. "All foreground service types are subject to review."
- "User perceptible means that the user should be aware that a foreground service task is running on their device." 사용자가 직접 시작했거나, 명확한 상태바 알림이 있으면 충족됨.
  - 출처: https://support.google.com/googleplay/android-developer/answer/13392821?hl=en / 확신도: 높음
- Device and Network Abuse 정책상 FGS는 다음을 모두 충족해야 함: 핵심 기능과 관련되고 사용자에게 이로울 것("beneficial to the user and relevant to the core functionality"), "initiated by the user or is user perceptible", "can be terminated or stopped by the user", "runs only for as long as necessary to complete the task". Key Considerations는 WorkManager 같은 대안이 가능한지 먼저 검토하라고 함.
  - 출처: https://support.google.com/googleplay/android-developer/answer/16559646 / 확신도: 높음
- FGS 정책의 최근 변경: geofencing이 FGS 승인 사용 사례에서 빠짐(2027-01-27). 우리 앱과는 무관.
  - 출처: https://support.google.com/googleplay/android-developer/table/12921780?hl=en / 확신도: 높음

### 백그라운드 시작 제한

- 기본 규칙(target 31+): "Apps that target Android 12 (API level 31) or higher can't start foreground services while the app is running in the background, except for a few special cases." 위반하면 `ForegroundServiceStartNotAllowedException`.
- 우리 앱 관련 예외:
  - 사용자가 보이는 상태(activity)에서 시작
  - 사용자가 앱의 UI 요소(bubble, notification, widget, activity)를 조작
  - BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, MY_PACKAGE_REPLACED 수신
  - 배터리 최적화 해제
  - SYSTEM_ALERT_WINDOW 보유
- **Android 15 SAW 규칙(target 35+)**: "it must have the SYSTEM_ALERT_WINDOW permission *and* the app must currently have a visible overlay window." 즉 "the app needs to first launch a TYPE_APPLICATION_OVERLAY window *and* the window needs to be visible before you start a foreground service." 가시성은 `View.getWindowVisibility()` 또는 `onWindowVisibilityChanged()`로 확인함.
  - 출처: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start , https://developer.android.com/about/versions/15/behavior-changes-15 / 확신도: 높음
- 이 예외 목록에 "accessibility service가 바인딩된 경우"는 없음. 접근성 서비스를 FGS 재시작 근거로 쓰지 말 것.
  - 출처: 위 restrictions-bg-start 페이지 / 확신도: 중간(목록에 없다는 사실만 확인함)

### BOOT_COMPLETED (Android 14~17)

- Android 15(target 35)에서는 BOOT_COMPLETED 리시버가 **dataSync, camera, mediaPlayback, phoneCall, mediaProjection, microphone** 타입을 시작할 수 없음(microphone은 Android 14부터). 위반하면 `ForegroundServiceStartNotAllowedException`. 테스트: `adb shell am compat enable FGS_BOOT_COMPLETED_RESTRICTIONS <pkg>`.
- **specialUse는 이 목록에 없음.** 따라서 문서상으로는 부팅 후 "모니터링 ON 상태였으면 재시작"이 가능함.
- Android 16과 17의 behavior-change 페이지(targeting 버전 / all apps 모두)에서는 FGS 부팅 관련 추가 제한을 찾지 못함.
  - 출처: https://developer.android.com/about/versions/15/behavior-changes-15 , https://developer.android.com/about/versions/16/behavior-changes-all , https://developer.android.com/about/versions/17/behavior-changes-17 , https://developer.android.com/about/versions/17/behavior-changes-all
  - 확신도: 높음(15 규칙), 중간(16/17은 "없음을 확인함" 수준)

### 타임아웃

- dataSync와 mediaProcessing(target 35+): "run for a total of 6 hours in a 24-hour period". 이후 `Service.onTimeout(int,int)`가 호출되고, 몇 초 안에 `stopSelf()`하지 않으면 `RemoteServiceException: A foreground service of type ... did not stop within its timeout`. 앱을 포그라운드로 가져오면 타이머가 초기화됨. 한도를 소진한 뒤 재시작하면 "Time limit already exhausted for foreground service type dataSync".
- shortService는 약 3분.
- **specialUse 타임아웃은 문서에 없음** (timeout 페이지: "Currently, this restriction only applies to dataSync and mediaProcessing").
  - 출처: https://developer.android.com/develop/background-work/services/fgs/timeout / 확신도: 높음
- Android 16(모든 앱): "Background jobs started from a foreground service now must adhere to their respective runtime quotas... including ... WorkManager". FGS가 돈다고 WorkManager 작업 quota가 면제되지 않음.
  - 출처: https://developer.android.com/develop/background-work/services/fgs/changes / 확신도: 높음

### 기타 FGS 동작

- Android 13 이상에서는 사용자가 Task Manager(Active apps)에서 "Stop"을 누를 수 있음. 이때 "your entire app stops". 콜백이 없으므로 다음 실행 때 `ApplicationExitInfo.REASON_USER_REQUESTED`를 확인해야 함. 예약된 job/alarm은 계속 실행됨.
  - 출처: https://developer.android.com/develop/background-work/services/fgs/handle-user-stopping / 확신도: 높음
- Android 14: `setOngoing(true)`인 FGS 알림도 사용자가 스와이프로 지울 수 있음. 예외는 잠금 화면, Clear all, CallStyle/DPC/미디어 알림.
  - 출처: https://developer.android.com/about/versions/14/behavior-changes-all / 확신도: 높음
- POST_NOTIFICATIONS가 없어도 FGS 시작은 가능함. 다만 거부되면 FGS 알림이 알림창에는 안 보이고 Task Manager에만 보임. MessagingStyle 알림은 이 권한이 있어야 표시됨.
  - 출처: https://developer.android.com/develop/ui/views/notifications/notification-permission / 확신도: 높음
- 알림 priority는 PRIORITY_LOW 이상이어야 함. 매니페스트에 선언하지 않은 타입을 `startForeground`에 넘기면 `IllegalArgumentException`, 타입별 권한이 없으면(target 34+) `SecurityException`.
  - 출처: https://developer.android.com/develop/background-work/services/fgs/launch , https://developer.android.com/develop/background-work/services/fgs/changes / 확신도: 높음
- Target API 요구: 2026-08-31부터 신규 앱과 업데이트는 **API 36(Android 16) 이상**이어야 함(연장 시 2026-11-01까지).
  - 출처: https://developer.android.com/google/play/requirements/target-sdk / 확신도: 높음

### 구체적인 함정(pitfall) 목록

1. WorkManager 작업이나 알림 리시버에서 앱이 백그라운드일 때 `startForegroundService()`를 호출하면 `ForegroundServiceStartNotAllowedException`이 남. FGS는 "모니터링 ON" 버튼(activity가 보이는 상태)에서만 시작할 것.
2. target 35+에서는 "오버레이 권한이 있으니 백그라운드에서 FGS 시작 가능"이라는 가정이 틀림. 오버레이가 실제로 보이는 중이어야 함.
3. BOOT_COMPLETED 재시작은 specialUse라서 가능함. 실수로 dataSync를 같이 선언하면 막힘. 타입을 하나로 유지할 것.
4. `onTimeout`을 구현하지 않은 상태에서 dataSync를 섞어 쓰면 6시간 뒤 크래시(RemoteServiceException).
5. Task Manager "Stop"이나 알림 스와이프로 서비스가 사라질 수 있음. 앱을 다시 열 때 상태를 점검하고 사용자에게 다시 켜도록 안내할 것.
6. Play specialUse 심사에서 매니페스트 property 문구, 콘솔 설명, 동영상(사용자가 직접 모니터링을 켜는 장면, 상태 알림, 끄는 방법)이 서로 일치하지 않으면 거절 위험이 있음.
7. Android 16+에서는 FGS가 실행 중이어도 WorkManager quota가 적용됨. 리마인더 빈도를 낮게 유지할 것.
8. 폴링 루프는 화면이 꺼지면 반드시 멈출 것(SCREEN_NON_INTERACTIVE). "runs only for as long as necessary" 요건이 있고, Android 17의 앱 메모리 한도(REASON_OTHER, "MemoryLimiter:AnonSwap")도 고려해야 함.
   - 출처(메모리 한도): https://developer.android.com/about/versions/17/behavior-changes-all / 확신도: 높음

---

## 2. UsageStatsManager

- **권한**: "Most methods on this API require the permission android.permission.PACKAGE_USAGE_STATS. However, declaring the permission implies intention to use the API and the user of the device still needs to grant permission through the Settings application. See Settings#ACTION_USAGE_ACCESS_SETTINGS." 자기 패키지 정보만 반환하는 메서드(`getAppStandbyBucket`, `queryEventsForSelf`)는 이 권한이 필요 없음.
  - 출처: 구글 원문 javadoc 인용 미러 https://learn.microsoft.com/en-us/dotnet/api/android.app.usage.usagestatsmanager (원본 https://developer.android.com/reference/android/app/usage/UsageStatsManager) / 확신도: 높음
- **queryEvents(long, long)**: "Query for events in the given time range. Events are only kept by the system for a few days." 사용자가 unlocked 상태가 아니면(`UserManager#isUserUnlocked()`가 false) `null`을 반환함(Android R+). null 처리가 필수.
  - 출처: AOSP https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageStatsManager.java / 확신도: 높음
- **API 35에서 `queryEvents(UsageEventsQuery)` 추가**: API diff 34→35에 "Added methods: UsageEvents queryEvents(UsageEventsQuery)"가 있음. `UsageEventsQuery.Builder(begin, end).setEventTypes(...).setPackageNames(...)`를 쓰며, `@RequiresPermission(PACKAGE_USAGE_STATS)`, `@Nullable`, `@FlaggedApi(FLAG_FILTER_BASED_EVENT_QUERY_API)`가 붙어 있음. 문서는 이벤트 타입과 패키지를 지정하라고 권장함(비우면 전체 조회).
  - 출처: https://developer.android.com/sdk/api_diff/35-incr/changes/android.app.usage.UsageStatsManager , https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/usage/UsageEventsQuery.java
  - 확신도: 높음(메서드 추가), **낮음(`setPackageNames`가 API 35에 이미 있었는지 36에 추가됐는지는 미확인)**. SDK_INT 분기를 두고 구버전에서는 `queryEvents(long,long)`로 폴백할 것.
- **이벤트 타입**: `MOVE_TO_FOREGROUND`(1)와 `MOVE_TO_BACKGROUND`(2)는 API 29에서 deprecated됨 → `ACTIVITY_RESUMED`(1)/`ACTIVITY_PAUSED`(2)를 사용(정수 값 동일). API 29에서 `ACTIVITY_STOPPED`(23), `FOREGROUND_SERVICE_START/STOP`(19/20), `DEVICE_SHUTDOWN/STARTUP`(26/27)이 추가됨. 그 밖에 `SCREEN_INTERACTIVE`(15)/`SCREEN_NON_INTERACTIVE`(16), `KEYGUARD_SHOWN`(17)/`KEYGUARD_HIDDEN`(18). ACTIVITY_RESUMED 설명: "If a package has multiple activities, this event is reported for each activity that moves to foreground. This event is corresponding to Activity.onResume()". 액티비티 단위 이벤트이므로 패키지 단위 상태머신으로 묶어야 함.
  - 출처: https://developer.android.com/sdk/api_diff/29/changes/android.app.usage.UsageEvents.Event , https://learn.microsoft.com/tr-tr/dotnet/api/android.app.usage.usageeventtype?view=net-android-37.0 / 확신도: 높음
- **Android 15/16/17 변경**: 15/16/17 behavior-change 페이지와 17 summary(2026-10-09 갱신)에 UsageStats 변경 항목이 없음. Android 14 문서는 `queryEventsForSelf()`를 standby bucket 진단 용도로만 언급함.
  - 출처: https://developer.android.com/about/versions/17/summary , https://developer.android.com/about/versions/14/behavior-changes-all / 확신도: 중간("변경 없음"을 확인한 수준)
- **이벤트 전달 지연**: 공식 문서에서 지연이나 truncation에 대한 설명을 찾지 못함. **미확인.** 매 폴링마다 "최근 N분" 윈도우를 다시 조회해 상태를 재구성하는 방식을 권장(지연 이벤트에 강함).
- **권한 확인 방법**: `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg) == MODE_ALLOWED`가 통상적 방법. **이번 조사에서 공식 문서로 확인하지 못함(일반 지식).** 확신도: 중간. 앱의 onResume마다 재확인하고, 꺼져 있으면 FGS를 중지한 뒤 설정 화면(`ACTION_USAGE_ACCESS_SETTINGS`)으로 안내.
- **Play 정책**: PACKAGE_USAGE_STATS 전용 선언서는 찾지 못함. "Permissions and APIs that Access Sensitive Information" 정책과 "Restricted Permissions with minimum scope alternatives"(사진, 연락처, 로컬 네트워크 등) 어디에도 이 권한은 언급되지 않음. 다만 User Data 정책이 "inventory of other apps on the device"와 "sensitive device or usage data"를 개인/민감 데이터로 분류함. 따라서 개인정보처리방침, Data safety, (기대 범위 밖이면) 앱 내 Prominent Disclosure 대상임. Disclosure는 "Must be within the app itself", "displayed in the normal usage of the app", 런타임 권한 요청 직전에 있어야 하고, 다른 고지와 묶을 수 없으며, back/home으로 닫는 것은 동의로 보지 않음.
  - 출처: https://support.google.com/googleplay/android-developer/answer/16558241?hl=en , https://support.google.com/googleplay/android-developer/answer/16935362 , https://support.google.com/googleplay/android-developer/answer/10144311?hl=en
  - 확신도: 중간(선언서가 "없다"는 것은 검색으로 못 찾았다는 의미)
- **QUERY_ALL_PACKAGES는 쓰지 말 것**: "You may not use QUERY_ALL_PACKAGES if your app can operate with a more targeted scoped package visibility declaration." 추적 대상 패키지를 `<queries><package .../></queries>`로 명시할 것.
  - 출처: https://support.google.com/googleplay/android-developer/answer/16558241?hl=en / 확신도: 높음
- Data safety의 정확한 카테고리 명칭(예: "App activity > App interactions / Installed apps")은 이번에 확인하지 못함. **미확인.**

---

## 3. AccessibilityService (정밀 모드)

### Play 정책 현재 상태

- **isAccessibilityTool은 설정하면 안 됨**: 장애인 지원이 주목적인 서비스만 선언할 수 있음. "General assistants, antivirus software, automation tools, password managers, and launchers do not qualify"(요지). 우리 앱은 **non-accessibility tool**이므로 prominent disclosure와 consent가 필수.
- **Prominent disclosure 요건(접근성)**:
  - 앱 안에 있어야 함(스토어 설명, 웹사이트, 처리방침만으로는 불가)
  - 정상 사용 흐름에서 표시
  - "Must describe the data being accessed or collected through the AccessibilityService API"
  - "Must explain how the data will be used and/or shared"
  - "Must require affirmative user action for consent"
  - "Cannot only be placed in a privacy policy or terms of service"
  - 다른 개인정보 고지와 묶을 수 없음
  - `AccessibilityService_description`이나 `htmlDescription`으로 대체할 수 없음
- **Play Console 선언(non-tool)**: API가 필요한 이유, 개인/민감 데이터 수집·공유 여부, prominent disclosure와 consent 흐름을 보여주는 동영상(**거절하는 경우 포함**)을 제출함. 사용 방식이 바뀌면 다시 제출해야 하며, 선언되지 않은 사용은 정지나 계정 해지 사유가 될 수 있음. 최초 선언 의무는 "Starting November 3, 2021, apps that target API level 31"부터.
  - 출처: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en / 확신도: 높음
- **최소 범위 원칙**: "Apps must use more narrowly scoped APIs and permissions in lieu of the Accessibility API when possible". 금지 사항: "Work around Android built-in platform security controls, privacy controls and notifications". 근거로 "UsageStats로는 YouTube 앱 안에서 Shorts 화면인지 일반 화면인지 구분할 수 없음"을 선언서와 영상에 명시할 것.
  - 출처: https://support.google.com/googleplay/android-developer/answer/16558241?hl=en / 확신도: 높음
- **자율 행동(AI/agent) 금지 조항**:
  - 발표: "Policy announcement: October 30, 2025". "we are updating our Accessibility API policy to clarify and reinforce our existing rules that any use of this API that enables an app to autonomously initiate, plan, and execute actions is prohibited." 준수 기한은 "at least 30 days from October 30, 2025".
    - 출처: https://support.google.com/googleplay/android-developer/answer/16550159
  - 현재 정책 본문: "Any use of the Accessibility API that enables an app to autonomously initiate, plan, and execute actions or decisions" is "strictly prohibited". 단 "This does not prohibit deterministic, rule-based automation, where behavior follows a static, human-defined script"(예: "If Trigger X occurs, perform Action Y"). 검증된 isAccessibilityTool 앱만 예외.
    - 출처: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en
  - 2026년 후속: "Policy announcement: October 29, 2026"에서 "Clarified the policy summary on the isAccessibilityTool flag usage and disclosure requirements based on app designation"(준수 기한 "at least 30 days from October 29, 2026"). 정책 요약은 이 API를 "power autonomous decision-making agents"에 쓸 수 없다고 명시함.
    - 출처: https://support.google.com/googleplay/android-developer/answer/18100766?hl=en , https://support.google.com/googleplay/android-developer/answer/16585319
  - 확신도: 높음(문구), **중간(10-29-2026 "clarification"의 세부 문구는 공지에 요약만 있고 diff를 확인하지 못함)**
  - 참고: 요청서에서 말한 "2026 AI/자율행동 정책 변경"에 해당하는 독립된 2026년 시행일은 찾지 못함. 자율행동 금지는 2025-10-30에 발표됐고, 2026-10-29에는 isAccessibilityTool 지정에 따른 고지 요건이 명확화됨.

### OS 제한 (Android 16/17)

- **Android 17 Advanced Protection Mode(AAPM)**: "removing access to the accessibility service from all apps that are not labeled as accessibility tools" (Google Security Blog, 2026-05-12). Google 블로그(2026-10-01)는 "automatically restricts AccessibilityService access exclusively to verified applications categorized as Accessibility Tools". Android 도움말은 "Restricts accessibility services to verified accessibility tools." AAPM은 사용자가 켜는 옵션(opt-in)임. 켜면 기존 권한이 회수되고 새로 부여할 수 없음(Android Authority 베타 2 테스트, 비공식).
  - 출처: https://blog.google/security/whats-new-in-android-security-privacy-2026/ , https://blog.google/security/android-advanced-protection-updates/ , https://support.google.com/android/answer/16339980?hl=en , (비공식) https://www.androidauthority.com/android-17-beta-2-advanced-protection-mode-accessibility-apps-3648860
  - 확신도: 높음(제한 존재), 중간(회수 동작의 세부)
  - 대응: `AdvancedProtectionManager.isAdvancedProtectionEnabled` + `QUERY_ADVANCED_PROTECTION_MODE` 권한으로 감지하고, AAPM이 켜져 있으면 정밀 모드를 비활성화한 뒤 UsageStats 기본 모드로 동작하도록 안내.
    - 출처: https://developer.android.com/privacy-and-security/advanced-protection-mode / 확신도: 높음
- Android 16: `accessibilityDataSensitive` 뷰는 non-tool 서비스의 `dispatchGesture`를 차단함(우리는 gesture를 쓰지 않으므로 무관). `announceForAccessibility`는 deprecated(우리 앱 UI에서 쓰지 말 것).
  - 출처: https://developer.android.com/privacy-and-security/risks/tapjacking , https://developer.android.com/about/versions/16/behavior-changes-all / 확신도: 높음
- Restricted settings: 설치 후 일부 설정(대표 예: 접근성)이 잠겨 있을 수 있고 "Allow restricted settings"로 해제함. 공식 페이지는 대상 설치 경로를 명시하지 않음. 일반적으로 사이드로드 APK 테스트 때 이 문제를 겪음(비공식).
  - 출처: https://support.google.com/android/answer/12623953?hl=en / 확신도: 중간

### 우리 사용("Shorts/Reels 화면 감지만")은 허용되는가

- 판단: **조건부 허용 가능**(확신도: 중간). 이유는 다음과 같음.
  - 자율 행동이 아님. HOME 이동은 접근성 `performGlobalAction`이 아니라 우리 앱의 Intent로 하고, 사용자가 버튼을 눌러야 실행됨.
  - 결정 규칙이 정해진 규칙(시간 임계값 등)임.
  - 패키지가 제한되고 저장하지 않음.
  - 그러나 non-tool의 접근성 사용은 심사 재량이 큼.
- 반드시 지킬 것:
  - (a) LLM 출력이 접근성 동작을 일으키지 않도록 할 것. 접근성 서비스에는 동작 API를 아예 넣지 말 것(`canPerformGestures` 미사용, `performGlobalAction` 미사용).
  - (b) `android:packageNames`를 YouTube/Instagram/TikTok 패키지로 제한.
  - (c) 이벤트는 `typeWindowStateChanged|typeViewScrolled`만 받음(`typeAllMask` 금지. 문서도 "resource intensive"라고 경고함).
  - (d) 노드 트리 조회가 필요하면 `canRetrieveWindowContent=true`가 되는데, 그 경우 disclosure에 "화면 구조(뷰 ID/클래스명) 확인"을 명시해야 함.
  - (e) 기능은 선택 사항이며 거절해도 기본 모드로 동작해야 함(영상에 거절 흐름 포함).
  - 출처: https://developer.android.com/guide/topics/ui/accessibility/service
- **Disclosure 예시(한국어, 별도 화면, "동의하고 설정으로 이동" / "사용 안 함" 버튼)**:
  > 정밀 모드는 Android 접근성 서비스를 사용합니다. 켜면 이 앱은 YouTube, Instagram, TikTok이 앞에 있을 때만 현재 화면이 Shorts·Reels·TikTok 피드인지 알기 위해 화면 전환·스크롤 이벤트와 화면 구성 요소 이름을 확인합니다. 화면의 글자, 영상, 댓글, 계정 정보는 읽거나 저장하지 않습니다. 판단은 기기 안에서만 이루어지며, 결과(숏폼 화면 여부)는 저장하거나 서버·AI로 보내지 않습니다. 이 앱은 접근성 서비스로 다른 앱을 조작하지 않습니다. 정밀 모드는 언제든 끌 수 있고, 꺼도 기본 기능(앱 사용 시간 기반 알림)은 그대로 사용할 수 있습니다.
  - 근거 요건: https://support.google.com/googleplay/android-developer/answer/10964491?hl=en , https://support.google.com/googleplay/android-developer/answer/10144311?hl=en (문안은 제안이며 공식 예시가 아님)

---

## 4. 오버레이, 알림, UI 동작 변경

### 오버레이 (SYSTEM_ALERT_WINDOW / TYPE_APPLICATION_OVERLAY)

- **Untrusted touch 차단(Android 12+)**: `FLAG_NOT_TOUCHABLE` 등으로 터치를 통과시키는 창이 다른 앱을 가리면 그 터치는 차단됨. "Windows of type TYPE_APPLICATION_OVERLAY aren't trusted." 통과가 허용되는 경우: 창이 GONE/INVISIBLE, alpha 0.0, 또는 "combined opacity is less than or equal to the system's maximum obscuring opacity for touches. In Android 12, this maximum opacity is 0.8 by default." 차단되면 로그에 "Untrusted touch due to occlusion by PACKAGE_NAME"이 남음.
  - 출처: https://developer.android.com/about/versions/12/behavior-changes-all / 확신도: 높음
  - 대응: 버블과 카드는 작은 **터치 가능한 창**(통과형 아님)으로 만들 것. 전면을 덮는 통과형 반투명 레이어는 alpha 0.8 이하로 하거나 쓰지 말 것.
- 다른 앱은 `Window.setHideOverlayWindows(true)`(Android 12+)로 오버레이를 숨길 수 있음. 일부 앱 화면에서 버블이 안 보일 수 있으니 실패해도 동작하도록(fail-soft) 설계.
  - 출처: https://developer.android.com/privacy-and-security/risks/tapjacking / 확신도: 중간(숨김 범위 세부는 미확인)
- Android 15/16/17 behavior-change 페이지에서 TYPE_APPLICATION_OVERLAY 자체에 대한 새 제한은 찾지 못함. 관련 변경은 15의 "SAW FGS 시작에는 보이는 오버레이 필요"뿐임.
  - 출처: 15/16/17 behavior-change 페이지 / 확신도: 중간
- **Background activity start(HOME Intent)**: 예외 목록에 "The app has the SYSTEM_ALERT_WINDOW permission granted by the user"가 있으므로 오버레이의 "그만 보기"에서 HOME을 시작하는 것은 허용됨. Android 14/15에서는 PendingIntent의 BAL 권한 위임이 sender/creator opt-in 방식으로 바뀜. Android 17(target 37)부터 `IntentSender.sendIntent()`는 sender 쪽 opt-in이 필요하고 task hijacking 보호가 적용됨. **HOME은 PendingIntent가 아니라 직접 `startActivity`로 시작할 것.**
  - 출처: https://developer.android.com/guide/components/activities/background-starts , https://developer.android.com/about/versions/17/behavior-changes-17 / 확신도: 높음
- **Play 정책**: SAW 전용 선언서는 없음. Key Considerations: "Direct users to the system settings page for approval of special permissions (for example, SYSTEM_ALERT_WINDOW)". Device and Network Abuse 정책은 다른 앱을 방해하는 앱을 금지함("interfere with, disrupt, damage..."). 우리 앱은 다른 앱을 제어하지 않고 HOME으로만 이동하며, 광고 가리기 같은 동작은 하지 않음.
  - 출처: https://support.google.com/googleplay/android-developer/answer/16558241?hl=en , https://support.google.com/googleplay/android-developer/answer/16559646 / 확신도: 높음

### Edge-to-edge, predictive back, 대화면

- Android 15(target 35): 기본으로 edge-to-edge가 적용됨. `setStatusBarColor`는 효과가 없으므로 insets를 처리해야 함.
- Android 16(target 36): `windowOptOutEdgeToEdgeEnforcement`가 deprecated되고 비활성화되어 opt-out할 수 없음.
  - 출처: https://developer.android.com/about/versions/15/behavior-changes-15 , https://developer.android.com/about/versions/16/behavior-changes-16 / 확신도: 높음
- Predictive back(target 36, Android 16 기기): 시스템 애니메이션이 기본으로 켜짐. "onBackPressed is not called and KeyEvent.KEYCODE_BACK is not dispatched anymore." 임시 opt-out은 `android:enableOnBackInvokedCallback="false"`. Android 16에서는 3버튼 내비게이션에서도 predictive back이 동작함. 대응: `OnBackPressedCallback`이나 Compose `BackHandler`를 사용.
  - 출처: 같은 페이지 + https://developer.android.com/about/versions/16/behavior-changes-all / 확신도: 높음
- 대화면(sw≥600dp): target 36부터 orientation/resizable 제한이 무시됨. target 37부터는 opt-out이 사라짐. 세로 고정을 가정하지 말 것.
  - 출처: https://developer.android.com/about/versions/17/behavior-changes-17 / 확신도: 높음

### 알림

- POST_NOTIFICATIONS는 Android 13+ 런타임 권한임. 보내기 전에 `areNotificationsEnabled()`를 확인.
  - 출처: https://developer.android.com/develop/ui/views/notifications/notification-permission / 확신도: 높음
- **MessagingStyle / 대화(conversation)**: 대화 섹션에 표시되려면 MessagingStyle이어야 하고, target 30+에서는 "associated with a valid long-lived dynamic or cached sharing shortcut"(Person 정보 포함, `setShortcutId()`)이 필요함. Bubble도 shortcut이 있어야 함. 문서는 대화를 실시간 사람 간 소통(SMS, 채팅, 전화)으로 설명함.
  - 우리 앱의 AI 코치 메시지는 사람 간 대화가 아님. shortcut 없이 MessagingStyle만 쓰면 일반 알림으로 표시되는 것으로 보임(문서에서 추론). 대화 섹션 등록은 피하는 편이 안전함.
  - 발신자 Person 이름은 "앱 이름/AI 코치"로 명확히 할 것(실제 사람처럼 보이지 않게).
  - 출처: https://developer.android.com/develop/ui/views/notifications/conversations / 확신도: 중간(우리 앱에 대한 적용 판단은 추론)
- Notification cooldown(Android 15부터 사용자 설정): 같은 앱에서 연속 알림이 오면 최대 약 2분간 소리와 진동을 낮춤. 통화, 알람, 우선순위 대화는 제외. Android 16 DP에서 기본 ON처럼 보였다는 보도가 있음. **비공식 출처만 있음.**
  - 출처: https://9to5google.com/2024/11/19/notification-cooldown-android-16-dp1/ , https://androidauthority.com/android-16-notification-cooldown-3501276 / 확신도: 낮음
- Android 16의 강제 알림 그룹핑(그룹 키가 없어도 같은 앱 알림을 묶음): **비공식 출처만 있음.**
  - 출처: https://androidauthority.com/android-16-force-group-notifications-3565400 / 확신도: 낮음
  - 대응: 넛지 알림을 짧은 간격으로 연속 발송하지 말 것. 채널을 분리하고 그룹 키를 명시할 것.
- Android 17(target 37): 알림 custom view와 RemoteViews(위젯)의 비트맵 메모리 한도를 엄격히 적용함. 위반하면 `IllegalArgumentException`.
  - 출처: https://developer.android.com/about/versions/17/summary , https://developer.android.com/about/versions/17/behavior-changes-17 / 확신도: 높음
- **Play AI 생성 콘텐츠 정책**: AI 생성 콘텐츠가 있는 앱은 "in-app user reporting or flagging features"를 제공해야 함(앱을 나가지 않고 신고). AI 카드와 메시지에 "신고" 버튼을 둘 것.
  - 출처: https://support.google.com/googleplay/android-developer/answer/13985936?hl=en / 확신도: 높음

---

## 5. 패키지명

| 앱 | 패키지명 | 확인 |
|---|---|---|
| YouTube | `com.google.android.youtube` | Play 리스팅(Google LLC) |
| Instagram | `com.instagram.android` | Play 리스팅(Meta Platforms) |
| Instagram Lite | `com.instagram.lite` | Play 리스팅(Meta, 500M+) |
| TikTok (글로벌) | `com.zhiliaoapp.musically` | Play 리스팅(TikTok Pte. Ltd., 1B+). KR 스토어 페이지에서는 설치 버튼이 보이지 않음 |
| TikTok (아시아, **한국 스토어 표시본**) | `com.ss.android.ugc.trill` | KR 페이지에 "TikTok 틱톡 - 숏폼 영상, 크리에이터, 수익창출"과 설치 버튼이 있음 |
| TikTok Lite | `com.zhiliaoapp.musically.go` | Play 리스팅(1B+) |
| TikTok Lite (신규) | `com.ss.android.ugc.tiktok.lite` | Play 리스팅(10M+) |
| Douyin (중국) | `com.ss.android.ugc.aweme` | **미확인(일반 지식, Play 미배포)** |

- 출처: https://play.google.com/store/apps/details?id=com.google.android.youtube , https://play.google.com/store/apps/details?id=com.instagram.android , https://play.google.com/store/apps/details?id=com.instagram.lite , https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically , https://play.google.com/store/apps/details?id=com.ss.android.ugc.trill&hl=ko&gl=KR , https://play.google.com/store/apps/details?id=com.zhiliaoapp.musically.go , https://play.google.com/store/apps/details?id=com.ss.android.ugc.tiktok.lite
- 확신도: 높음(Douyin 제외)
- 권장: TikTok 패키지 4종과 Instagram 2종을 모두 추적 목록과 `<queries>`, 접근성 `packageNames`에 넣을 것. KR 스토어에서 musically 설치 버튼이 보이지 않은 것은 페이지 텍스트 기준이라 가용성을 단정할 수 없음(확신도: 중간).

---

## 6. Firebase AI Logic (Gemini Developer API)

- **Gradle**: `implementation(platform("com.google.firebase:firebase-bom:35.1.0"))`, `implementation("com.google.firebase:firebase-ai")`. 디버그용은 `firebase-appcheck-debug`. 초기화는 `Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-3.8-flash")`(문서 예시). 문서는 모델명을 Remote Config로 관리하라고 권장함.
  - 출처: https://firebase.google.com/docs/ai-logic/get-started?platform=android / 확신도: 높음
  - 프로덕션 Play Integrity provider 아티팩트(`firebase-appcheck-playintegrity`)는 **이번에 직접 확인하지 못함(일반 지식).** 확신도: 중간.
- **모델(2026-11 기준)**:
  - **추천 저가·고속: `gemini-3.5-flash-lite`**. Stable, 출시 2026-07-21. Firebase 표기는 "No earlier than 2027-07-21", ai.google.dev 표기는 "No shutdown date announced". 설명: "High-volume, cost-sensitive workhorse model". Developer API에서 Blaze가 필요 없음. ai.google.dev는 신규 프로젝트에 "3.5 Flash-Lite or 3.8 Flash"를 권장함.
  - 대안 `gemini-3.1-flash-lite`: Stable, 2026-05-07 출시, **종료 2027-05-07**, 대체 모델 3.5-flash-lite.
  - 고품질 `gemini-3.8-flash`: Stable, 2026-09-02 출시, no earlier than 2027-09-02.
  - 피할 것:
    - 2.0 Flash/Flash-Lite: 2026-06-01 종료
    - 2.5 계열: Developer API에서는 "limited to users who have actively used them in the past", 신규 프로젝트는 쓸 수 없음
    - `gemini-3.5-flash`: 3.6-flash로 자동 라우팅됨
    - 3.6/3.7-flash: "short-term availability"
    - preview 모델들
  - 출처: https://firebase.google.com/docs/ai-logic/models , https://ai.google.dev/gemini-api/docs/deprecations , https://ai.google.dev/gemini-api/docs/models / 확신도: 높음
- **가격(paid, 1M 토큰당)**:
  - 3.5-flash-lite: 입력 $0.30, 출력 $2.50
  - 3.1-flash-lite: 입력 $0.25, 출력 $1.50
  - 3.8-flash: 입력 $0.75 / 출력 $3.75(2026-12-31까지), 2027-01-01부터 $1.50 / $7.50
  - 세 모델 모두 free tier는 "Free of charge"
  - 출처: https://aistudio.google.com/docs/pricing (ai.google.dev/gemini-api/docs/pricing에서 리다이렉트) / 확신도: 중간(요약 도구 경유)
- **Structured output**: `generationConfig { responseMimeType = "application/json"; responseSchema = Schema.obj(...) }`. 지원 필드는 `enum, items, maxItems, nullable, properties, required`. Firebase SDK는 `optionalProperties`에 넣지 않은 필드를 모두 required로 취급함. 스키마 크기는 입력 토큰에 포함됨. MIME은 `application/json`, `text/x.enum`. 지원 모델에 3.5-flash-lite, 3.1-flash-lite, 3.8-flash, 4-argon이 포함됨.
  - 출처: https://firebase.google.com/docs/ai-logic/generate-structured-output?platform=android / 확신도: 높음
- **Function calling**: `FunctionDeclaration` + `Tool.functionDeclarations(...)`, 최대 128개. "The model does not call the function directly"이므로 앱이 실행하고 `FunctionResponsePart`로 결과를 돌려줌. 병렬 호출 가능. Android의 자동 function calling은 문서에서 확인하지 못함. 지원 모델에 3.5-flash-lite가 포함됨.
  - 출처: https://firebase.google.com/docs/ai-logic/function-calling?platform=android / 확신도: 높음
- **App Check(중요)**: "Starting November 2, 2026, Firebase App Check enforcement will be required to use Firebase AI Logic." 이 날짜에 자동으로 enforce가 켜지며, App Check가 없는 앱 버전의 요청은 차단됨. 연기는 최대 2027-01-21까지만 가능. 즉 **이미 시행 중.**
  - limited-use token: `Firebase.ai(backend = GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true)`. 리플레이 보호에는 "Android BoM v34.14.0+ (App Check v19.1.0+)"가 필요함. 토큰 TTL은 5분(조정 불가)이고 요청마다 새 토큰을 발급하므로 지연과 비용이 늘 수 있음. 리플레이 보호를 강제하기 전에 "Unverified: Reused token" 지표를 먼저 모니터링하라고 함.
  - 디버그 토큰은 커밋하거나 배포 빌드에 포함하지 말 것.
  - 출처: https://firebase.google.com/docs/ai-logic/app-check / 확신도: 높음
- **Spark(무료) 플랜**: billing 계정을 연결하지 않으면 Spark에 남고 Gemini Developer API free tier를 쓸 수 있음("limited access to certain models"). 3.5-flash-lite, 3.1-flash-lite, 3.8-flash 모두 Developer API에서 Blaze가 필요 없음. $300 체험 크레딧은 Gemini Developer API에 쓸 수 없음.
  - 출처: https://firebase.google.com/docs/ai-logic/pricing , https://firebase.google.com/docs/ai-logic/models / 확신도: 높음
- **Rate limit**: free tier의 모델별 RPM/RPD 수치는 공식 문서에 **게시되어 있지 않음**("view your active rate limits" in AI Studio). "Rate limits are applied per project, not per API key." RPD는 태평양 시간 자정에 초기화됨. Firebase AI Logic의 사용자당 기본 한도는 "100 RPM per user". 초과하면 429.
  - 출처: https://ai.google.dev/gemini-api/docs/rate-limits , https://firebase.google.com/docs/ai-logic/quotas / 확신도: 높음(수치 미게시 사실 포함)
- **데이터 사용(개인정보 문서에 중요)**:
  - 가격표 기준 "Used to improve our products": free tier **Yes**, paid tier **No**.
  - 약관(Unpaid Services): "Google uses the content you submit to the Services and any generated responses to provide, improve, and develop"; "human reviewers may read, annotate, and process your API input and output"(계정·키·프로젝트와 분리된 뒤); "**Do not submit sensitive, confidential, or personal information to the Unpaid Services.**"
  - Paid: "Google doesn't use your prompts or responses to improve our products". 남용 탐지 목적으로만 "logs prompts and responses for a limited period of time".
  - EEA/스위스/영국 사용자에게는 Paid Services만 제공 가능.
  - **연령: "You must be 18 years of age or older to use the APIs."** 또한 만 18세 미만을 대상으로 하거나 그들이 쓸 가능성이 높은 서비스에서 사용 금지(요지).
  - 약관 시행일: 2026-03-23.
  - Firebase 문서는 Developer API 사용이 이 약관을 따르고, Spark 프로젝트는 Unpaid Services 조항을 보라고 안내함.
  - 출처: https://ai.google.dev/gemini-api/terms , https://aistudio.google.com/docs/pricing , https://firebase.google.com/docs/ai-logic/data-governance / 확신도: 높음

---

## 7. ML Kit GenAI Prompt API (온디바이스 Gemini Nano)

- **상태**: Beta. 2026-01-28 1.0.0-beta1에서 Alpha를 졸업함. "not subject to any SLA or deprecation policy."
  - 출처: https://developers.google.com/ml-kit/release-notes , https://developers.google.com/ml-kit/genai/prompt/android / 확신도: 높음
- **아티팩트**: `com.google.mlkit:genai-prompt`.
  - 최신: **1.0.0-beta5**(2026-10-26, Function Calling Preview, Kotlin 2.3+ 필요). get-started 문서 예시는 1.0.0-beta4.
  - beta3(2026-07-14): Structured Output, System Instructions, Thinking Mode 추가, 출력 4K 토큰으로 상향.
  - minSdk 26. bootloader가 unlocked인 기기는 지원하지 않음. `checkStatus()`(AVAILABLE/DOWNLOADABLE/DOWNLOADING/UNAVAILABLE) 후 `download()`.
  - 입력은 "under 4000 tokens".
  - 출처: https://developers.google.com/ml-kit/genai/prompt/android/get-started , https://developers.google.com/ml-kit/release-notes / 확신도: 높음
- **지원 기기(문서 목록 발췌)**:
  - nano-v3: Pixel 9/10 전 라인업, Galaxy S26/S26+/S26 Ultra, OnePlus 15, OPPO Find X8/X9, vivo X200/X300 등
  - nano-v4: Pixel 11 전 라인업, Galaxy Z Flip8/Fold8/Fold8 Ultra
  - nano-v2: Galaxy Z Fold7/TriFold, Xiaomi 15/17, OnePlus 13 등
  - 이번에 받은 목록에 Galaxy S25는 없었음(추출 누락 가능성 있음).
  - 출처: https://developers.google.com/ml-kit/genai / 확신도: 중간
- **사용 제한**:
  - "GenAI API inference is permitted only when the app is the top foreground application." "Using the API when the app is not in the foreground, including using a foreground service, will result in an ErrorCode.BACKGROUND_USE_BLOCKED response."
  - 짧은 시간에 요청이 몰리면 `ErrorCode.BUSY`(exponential backoff 권장).
  - 장기 한도(예: 일일)를 넘으면 `ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED`. 문서 표현은 "AICore enforces an inference quota per app".
  - 출처: https://developers.google.com/ml-kit/genai / 확신도: 높음
- **한국어**: Prompt API 문서에 지원 언어 목록이 **없음 → 한국어 지원·품질 미확인**. 참고로 Summarization API는 "supports English, Japanese, and Korean". 언어 가용성은 "may vary depending on the particular device's configuration and the models that have been downloaded".
  - 출처: https://developers.google.com/ml-kit/genai/summarization/android , https://developers.google.com/ml-kit/genai / 확신도: 높음(목록이 없다는 사실), 낮음(실제 한국어 품질)
- 함의: 모니터링 FGS(백그라운드)에서 온디바이스 LLM으로 넛지 문구를 생성할 수 없음. 앱이 포그라운드일 때 미리 생성해 캐시하거나 템플릿을 쓸 것.

---

## 8. 한국 법률 (짧게)

- **현행 개인정보 보호법**: 법률 제21445호, 2026-03-10 일부개정, **2026-09-11 시행**.
  - 출처: https://www.law.go.kr/LSW/lsInfoP.do?lsId=011357&ancYnChk=0 / 확신도: 높음
- **제22조의2(아동의 개인정보 보호)**:
  - ① "개인정보처리자는 만 14세 미만 아동의 개인정보를 처리하기 위하여 이 법에 따른 동의를 받아야 할 때에는 그 법정대리인의 동의를 받아야 하며, 법정대리인이 동의하였는지를 확인하여야 한다."
  - ② 법정대리인 동의를 받는 데 필요한 최소 정보(대통령령으로 정함)는 아동에게서 직접 수집할 수 있음.
  - ③ 만 14세 미만에게 고지할 때는 "이해하기 쉬운 양식과 명확하고 알기 쉬운 언어"를 써야 함.
  - 확인 방법은 시행령 제17조의2(예: 문자로 동의 확인을 알리는 방법, 카드 정보를 받는 방법 등)에 있음.
  - 출처: 조문 텍스트는 2차 출처 https://korea.legal/wiki/개인정보-보호법-제22조의2/ (law.go.kr 본문은 JS 프레임이라 추출하지 못함), 확인 방법은 https://m.easylaw.go.kr/MOB/NtcInfoRetrieve.laf?ntcSeq=1012
  - 확신도: 중간~높음(공식 사이트의 원문 대조는 못 함)
- **2025~2026 개정 요지(2026-09-11 시행)**:
  - 반복·중대 위반 과징금 상한을 전체 매출액의 10%로 상향
  - 대표자의 최종 책임과 CPO 역할 강화
  - "유출 등"(위조·변조·훼손 포함) 개념 도입, 유출 의심 단계에서도 72시간 내 통지
  - 보호 투자 시 과징금 최대 40% 감경
  - 안전조치 위반 과태료 상향
  - **아동 동의 조항이나 앱 사용기록에 특화된 변경은 없음**(기사 기준)
  - 시행령 개정 이력: 2025-03-13, 2025-10-02, 2026-05-19, 2026-08-20, 2026-09-11 시행분이 있음
  - 출처: https://www.lawtimes.co.kr/news/articleViewAmp.html?idxno=226414 (법무법인 기고, 비공식), https://www.law.go.kr/lsRvsDocListP.do?lsId=011468
  - 확신도: 중간
- **국외 이전(제28조의8)**: 프롬프트가 Google 서버로 가므로 국외 이전 요건을 충족해야 함. 근거는 별도 동의, 계약 이행에 필요한 처리위탁·보관, 인증, 적정성 인정 등이며, 이전 근거와 동의 내용을 개인정보 처리방침에 공개해야 함.
  - 출처: https://pipc.go.kr/np/default/page.do?mCode=D060040000 / 확신도: 높음
- 정책 동향: 정부(방미통위)가 만 14세 미만 SNS 이용 제한과 연령확인 의무를 추진 중이지만, 2026-07-21 기준 법안이나 시행일은 확정되지 않음.
  - 출처: https://www.mt.co.kr/article/2026072110174549000 (언론) / 확신도: 중간
- **위치정보법**: 위치정보를 수집하지 않으면 해당 없음.
- 권장:
  - 가입 또는 첫 실행 시 연령 확인(만 14세 미만 차단)으로 법정대리인 동의 절차를 피할 것.
  - 다만 Gemini API 약관이 18세 이상을 요구하므로 **AI 기능 사용자(또는 앱 전체)를 만 18세 이상으로 제한**하고, Play 타깃 연령도 18+로 설정하는 편이 일관적임.

---

## 미확인 / 한계 목록

1. `UsageEventsQuery.Builder.setPackageNames`가 추가된 API 레벨(35인지 36인지)
2. UsageStats 이벤트 전달 지연에 대한 공식 수치나 설명. 찾지 못함.
3. AppOps 기반 usage access 확인 코드는 공식 문서로 재확인하지 못함(일반 지식).
4. Data safety 카테고리의 정확한 명칭(앱 사용 기록이 어디에 해당하는지)
5. Play Integrity App Check 아티팩트명(`firebase-appcheck-playintegrity`). 일반 지식.
6. Notification cooldown과 Android 16 강제 그룹핑. 비공식 언론 출처만 있음.
7. 2026-10-29 Accessibility "clarification"의 정확한 문구 변경분(diff)
8. ML Kit Prompt API의 한국어 지원·품질, 전체 지원 기기 목록(Galaxy S25 포함 여부)
9. Gemini free tier의 모델별 RPM/RPD 수치. 문서에 게시되지 않았고 AI Studio에서만 확인 가능.
10. Douyin 패키지명. 일반 지식.
11. 개인정보 보호법 조문은 law.go.kr 본문 대신 2차 출처 텍스트로 확인함.
12. 가격 수치는 요약 도구를 거친 값이므로 결제 전 AI Studio 가격 페이지에서 직접 재확인할 것.
