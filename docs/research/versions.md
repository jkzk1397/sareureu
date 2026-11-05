# Android starter kit: verified versions (as of 2026-11-05)

Every value below was checked on 2026-11-05 with WebFetch/WebSearch against the source listed. Nothing comes from memory.
"Date" is the release date shown by the source. "(yr inferred)" means the GitHub page showed day and month but no year.

## 1. IDE / build toolchain

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| Android Studio (stable) | **Rabbit 2, 2026.2.2** (build 2026.2.2.8) | 2026-10-28 | https://developer.android.com/studio/releases ; https://developer.android.com/studio/preview/features ; https://plugins.jetbrains.com/docs/intellij/android-studio-releases-list.html.md | The preview page lists Rabbit 2 as Stable, AGP 9.5.0 as Stable and Rabbit 3 as Canary (2026.2.3 Canary 2, Oct 28). The previous stable was Rabbit 1 (2026.2.1, Oct 1, 2026). Stable-release blog post dated Oct 28, 2026: https://developer.android.com/blog/posts/ai-agents-android-studio-integration |
| Android Gradle Plugin (stable) | **9.5.0** | Oct 2026 (page gives month only) | https://developer.android.com/build/releases/gradle-plugin | No 9.5.x patch listed yet. Supports up to API 37. Default NDK 28.2.13676358. |
| Gradle required by AGP 9.5 | **min 9.6.0 / default 9.6.0** | - | https://developer.android.com/build/releases/gradle-plugin | |
| Gradle latest stable | **9.8.2** | 2026-10-19 | https://gradle.org/releases/ | Kotlin 2.4.20 release notes say it supports Gradle 9.7.0. Pinning the wrapper to 9.6.0 to 9.8.2 meets AGP's minimum. |
| JDK (AGP requirement) | **17 (min and default)** | - | https://developer.android.com/build/releases/gradle-plugin | Robolectric 4.16 notes say that targeting SDK 36 "you need to use JDK 21", so use JDK 21 for running unit tests and CI. |
| SDK Build Tools (AGP 9.5) | 36.0.0 min/default | - | https://developer.android.com/build/releases/gradle-plugin | |
| AGP built-in Kotlin | Default since AGP 9.0 | - | https://developer.android.com/build/migrate-to-built-in-kotlin | Remove `org.jetbrains.kotlin.android`. kapt is incompatible; use KSP (or `com.android.legacy-kapt` at the AGP version). Can't be disabled in AGP 10. With built-in Kotlin, `jvmTarget` defaults to `compileOptions.targetCompatibility`. |

## 2. Kotlin / KSP / Compose compiler

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| Kotlin (stable) | **2.4.21** (bug-fix) | 2026-10-08 | https://kotlinlang.org/docs/releases.html | 2.4.20 (tooling) shipped 2026-09-07 and 2.4.0 (language) 2026-06-03. 2.5.0 is planned for Dec 2026. |
| KSP (com.google.devtools.ksp) | **2.3.13** | Maven metadata lastUpdated 2026-10-30 | https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml | New scheme: KSP 2.3.x is versioned independently of Kotlin, with no `2.x.y-1.0.z` suffix. Kotlin's KSP quickstart pairs Kotlin 2.4.21 with KSP 2.3.10 (https://kotlinlang.org/docs/ksp-quickstart.html). The GitHub releases page still showed 2.3.11 as "Latest" (Aug 3), so it is cached or lagging; Maven Central is authoritative. |
| Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` | **2.4.21** (= Kotlin version) | - | https://developer.android.com/develop/ui/compose/compiler | Docs: "this version matches your Kotlin version". The docs example uses 2.4.10. |
| kotlinx serialization plugin `org.jetbrains.kotlin.plugin.serialization` | **2.4.21** (= Kotlin version) | - | https://kotlinlang.org/docs/releases.html | Same versioning as Kotlin. |

## 3. Compose

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| Compose BOM `androidx.compose:compose-bom` | **2026.09.00** | (Sep 2026) | https://developer.android.com/develop/ui/compose/bom ; https://developer.android.com/develop/ui/compose/bom/bom-mapping | Newest entry on the mapping page. No 2026.10.x BOM is listed. |
| Compose ui / foundation / runtime (stable) | **1.12.1** | 2026-09-09 | https://developer.android.com/jetpack/androidx/releases/compose-ui ; https://developer.android.com/jetpack/androidx/releases/compose | 1.12.0 shipped 2026-08-12. 1.13.0-rc01 shipped 2026-11-04 and is not stable. BOM 2026.09.00 maps foundation/animation to 1.12.1. |
| material3 (stable, via BOM) | **1.4.0** | - | https://developer.android.com/jetpack/androidx/releases/compose-material3 ; bom-mapping page | 1.5.0-rc01 shipped 2026-11-04 and is not in a stable BOM. The mapping page lists material3 1.4.0 first for 2026.09.00 (matched by position). |
| material-icons-extended (via BOM) | 1.7.8 | - | bom-mapping page | Frozen. Prefer Material Symbols or vector drawables. |
| compose ui-test-junit4 / ui-test-manifest | via BOM (1.12.1 line) | - | https://developer.android.com/develop/ui/compose/bom | The mapping page excerpt for ui-test was truncated, so 1.12.1 is inferred from the ui group. |

## 4. Navigation

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| androidx.navigation3: navigation3-runtime / navigation3-ui | **1.2.1** (STABLE) | 2026-11-04 | https://developer.android.com/jetpack/androidx/releases/navigation3 | "1.2.0 is now stable!". 1.2.1 lowered compileSdk from 37 to 36. The alpha is 1.3.0-alpha04. Migration and get-started docs use `nav3Core = "1.2.0"`. Nav3 is designed exclusively for Compose. |
| androidx.lifecycle:lifecycle-viewmodel-navigation3 | **2.11.0** (stable) | 2026-06-17 | https://developer.android.com/jetpack/androidx/releases/lifecycle ; https://developer.android.com/guide/navigation/navigation-3/migration-guide | The migration guide uses 2.11.0. Get-started and the lifecycle snippet show 2.12.0-alpha05, so pin 2.11.0 for stable. Provides `rememberViewModelStoreNavEntryDecorator()`. |
| androidx.compose.material3.adaptive:adaptive-navigation3 | 1.4.0-alpha03 (alpha only) | - | https://developer.android.com/guide/navigation/navigation-3/get-started | Optional. Not stable. |
| androidx.navigation:navigation-compose | **2.10.2** | 2026-09-23 | https://developer.android.com/jetpack/androidx/releases/navigation | The release page says: "Caution: This library is in maintenance mode and will only receive critical fixes; new features are not planned." |
| **Recommendation** | **Navigation 3 (1.2.1)** for a new Compose-only app | - | same as above | The Nav2 release notes put Navigation in maintenance mode, and Nav3 is stable and actively developed. Caveat: the Navigation overview page (https://developer.android.com/guide/navigation) still says "If your app is built entirely with Jetpack Compose, use Navigation Compose", so the docs are inconsistent and no page explicitly says "use Nav3 for new apps". |

## 5. AndroidX

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| androidx.activity:activity-compose | **1.13.0** | 2026-03-11 | https://developer.android.com/jetpack/androidx/releases/activity | 1.14.0-alpha03 is the alpha. |
| androidx.lifecycle: lifecycle-runtime-compose, lifecycle-viewmodel-compose | **2.11.0** | 2026-06-17 | https://developer.android.com/jetpack/androidx/releases/lifecycle | 2.12.0-alpha05 shipped 2026-10-21 and is an alpha. |
| androidx.core:core-ktx | **1.19.1** | 2026-09-23 | https://developer.android.com/jetpack/androidx/releases/core | core-ktx is now an empty artifact; its APIs merged into `androidx.core:core`. Either works. |
| androidx.core:core-splashscreen | **1.2.0** | not shown | https://developer.android.com/jetpack/androidx/releases/core | |
| androidx.room (Room 2) | **2.8.5** | 2026-09-09 | https://developer.android.com/jetpack/androidx/releases/room | KSP: `ksp("androidx.room:room-compiler")`. minSdk 23 since 2.8.0. |
| androidx.room3 (Room 3) | **3.0.3** (STABLE) | 2026-09-09 (3.0.0: 2026-07-01) | https://developer.android.com/jetpack/androidx/releases/room3 | Uses `androidx.room3:room3-runtime` and `room3-compiler`, with plugin id `androidx.room3`. It is KSP-only and Kotlin-only (no Java codegen). DAOs must be suspend or Flow. A `SQLiteDriver` is required. 3.1.0-alpha03 is the alpha. The docs don't explicitly recommend Room 3 for new apps. |
| androidx.datastore:datastore-preferences | **1.3.0** | 2026-10-21 | https://developer.android.com/jetpack/androidx/releases/datastore | New `DataStore.Builder` API. |
| androidx.work:work-runtime(-ktx) | **2.12.0** | 2026-09-23 | https://developer.android.com/jetpack/androidx/releases/work | minSdk raised to 24. compileSdk must be 33 or higher. `work-runtime-ktx` is empty since 2.9.0, so `work-runtime` is enough. |
| androidx.hilt: hilt-lifecycle-viewmodel-compose, hilt-navigation-compose, hilt-work, hilt-compiler | **1.4.0** | 2026-07-01 | https://developer.android.com/jetpack/androidx/releases/hilt | `hiltViewModel()` moved to `hilt-lifecycle-viewmodel-compose` (package `androidx.hilt.lifecycle.viewmodel.compose`) in 1.3.0, with no navigation dependency. That's the right artifact with Nav3. 1.4.0 requires KGP 2.2.0 or later, and AGP 9.2.0 or later when used with Compose. The page doesn't mark hilt-navigation-compose as deprecated. |

## 6. Dagger / Hilt

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| com.google.dagger:hilt-android / hilt-compiler / Gradle plugin `com.google.dagger.hilt.android` | **2.60.1** | 2026-07-06 | https://repo1.maven.org/maven2/com/google/dagger/hilt-android/maven-metadata.xml ; https://github.com/google/dagger/releases | Dagger 2.59 added AGP 9 support for the Hilt plugin. 2.59.1 requires AGP 9.0.0 or later for the Hilt plugin. 2.60 built with Kotlin 2.3.21. |
| Dagger/Hilt KSP support | Stable as of **Dagger 2.60+ with KSP 2.3.9+** | - | https://dagger.dev/dev-guide/ksp.html | The docs don't use the word "KSP2". The KSP 2.3.x line it requires is KSP2 (see Uncertain). Limitation: KSP can't see types generated by Javac/KAPT processors. `androidx.hilt:hilt-compiler` KSP support is in 1.4.x. |

## 7. kotlinx

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| kotlinx-coroutines (core/android/test) | **1.11.0** | 2026-05-08 | https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/maven-metadata.xml ; https://github.com/Kotlin/kotlinx.coroutines/releases | Built with Kotlin 2.2.20. |
| kotlinx-serialization-json | **1.12.0** | 2026-10-19 | https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-serialization-core/maven-metadata.xml ; https://github.com/Kotlin/kotlinx.serialization/releases | Based on Kotlin 2.4.10. The json metadata URL failed, but the core metadata and GitHub release both show 1.12.0, and json releases in lockstep. |
| kotlinx-datetime | **0.8.0** | 2026-05-07 | https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-datetime-jvm/maven-metadata.xml ; https://github.com/Kotlin/kotlinx-datetime/releases | Still 0.x. `Instant`/`Clock` live in `kotlin.time` (moved in 0.7.0). `TimeZone` serialization is deprecated. Optional: `kotlin.time.Instant` may be enough. |

## 8. Firebase

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| Firebase Android BoM `com.google.firebase:firebase-bom` | **35.1.0** | 2026-10-21 | https://firebase.google.com/support/release-notes/android | BoM 35.0.0 raised minSdk to **24** for most libraries and removed firebase-ml-modeldownloader. KTX modules were removed in BoM 34.0.0. |
| Firebase AI Logic artifact | **`com.google.firebase:firebase-ai`** = **18.1.0** in BoM 35.1.0 | 2026-10-21 | https://firebase.google.com/docs/ai-logic/get-started?platform=android ; release notes | Gemini Developer API: `Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel("gemini-3.8-flash")` (the docs' example model). 18.0.0 was breaking: it removed `GenerativeBackend.vertexAI` in favor of `GenerativeBackend.agentPlatform`, raised minSdk to 24, and deprecated temperature/topK/topP for Gemini 3.x and later. |
| firebase-appcheck / firebase-appcheck-playintegrity / firebase-appcheck-debug | **20.0.0** (all, in BoM 35.1.0) | 2026-10-21 | https://firebase.google.com/support/release-notes/android ; https://firebase.google.com/docs/app-check/android/play-integrity-provider | Use the debug provider only in debug builds. |
| Google Services Gradle plugin `com.google.gms.google-services` | **4.5.0** | - | https://firebase.google.com/docs/android/google-services-plugin-and-file | Adds processing of reCAPTCHA site keys. |
| AI Logic limited-use App Check tokens (Android) | **Supported** | added in firebase-ai 17.2.0 (2025-08-28) | https://firebase.google.com/docs/ai-logic/app-check | `Firebase.ai(backend = GenerativeBackend.googleAI(), useLimitedUseAppCheckTokens = true)`. Replay protection needs BoM 34.14.0 or later (App Check 19.1.0+) and enforcement in Console > Security > App Check. Otherwise tokens are reusable until they expire after 5 minutes. |

## 9. ML Kit GenAI Prompt API (optional)

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| `com.google.mlkit:genai-prompt` | **1.0.0-beta5** | 2026-10-26 | https://developers.google.com/ml-kit/release-notes ; https://developers.google.com/ml-kit/genai/prompt/android | **Beta** (no SLA, may break). No GA exists. beta5 adds function calling (Preview). beta3 added structured output, system instructions, thinking mode and multi-image input. Alpha on 2025-10-29, beta1 on 2026-01-28. |
| Supported devices (Prompt API) | Gemini Nano v2/v3/v4 devices via AICore | - | https://developers.google.com/ml-kit/genai | nano-v3 covers the Pixel 9 and Pixel 10 series (incl. Pro Fold), Galaxy S26 series, OnePlus 15/15R, OPPO Find X8/X9, and more. nano-v4 covers the Pixel 11 series and Galaxy Z Flip8/Fold8. nano-v2 covers OnePlus 13/13s, Galaxy Z Fold7, Xiaomi 15/17, Honor Magic 7, and more. **Foreground only** (`BACKGROUND_USE_BLOCKED`). Needs a runtime feature check and fallback to Firebase AI. |

## 10. Testing / quality

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| junit:junit | **4.13.2** | 2021-02-13 | https://search.maven.org/solrsearch/select?q=g:junit+AND+a:junit&core=gav&rows=5&wt=json | Last JUnit 4. |
| app.cash.turbine:turbine | **1.2.1** | 2025-06-11 | https://repo1.maven.org/maven2/app/cash/turbine/turbine/maven-metadata.xml | No newer release. |
| org.robolectric:robolectric | **4.17** | 2026-09-10 | https://repo1.maven.org/maven2/org/robolectric/robolectric/maven-metadata.xml ; https://github.com/robolectric/robolectric/releases | "Robolectric 4.17 supports SDK 37". For SDK 36, the 4.16 notes say "you need to use JDK 21". |
| Spotless Gradle plugin `com.diffplug.spotless` | **8.11.0** | 2026-11-02 | https://repo1.maven.org/maven2/com/diffplug/spotless/spotless-plugin-gradle/maven-metadata.xml | |
| ktlint (`com.pinterest.ktlint:ktlint-cli`) | **1.8.0** | 2025-11-14 | https://repo1.maven.org/maven2/com/pinterest/ktlint/ktlint-cli/maven-metadata.xml ; https://github.com/pinterest/ktlint/releases | 2.0.0-ALPHA-4 (Aug 2026) is a pre-release; don't use it. Pin in Spotless with `ktlint("1.8.0")`. |
| androidx.test.ext:junit (+ junit-ktx) | **1.3.0** | 2025-07-30 | https://developer.android.com/jetpack/androidx/releases/test | 1.4.0-alpha03 (2026-10-29) is an alpha. |
| androidx.test.espresso:espresso-core | **3.7.0** | 2025-07-30 | https://developer.android.com/jetpack/androidx/releases/test | 3.8.0-alpha03 is an alpha. |
| androidx.test: core / runner / rules | **1.7.0** | 2025-07-30 | https://developer.android.com/jetpack/androidx/releases/test | orchestrator 1.6.1, monitor 1.8.0, services 1.6.0. |
| Compose UI test (ui-test-junit4, ui-test-manifest) | via Compose BOM 2026.09.00 | - | https://developer.android.com/develop/ui/compose/bom | |

## 11. GitHub Actions

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| actions/checkout | **v7** (v7.0.1) | 07-20 (yr inferred 2026) | https://github.com/actions/checkout/releases | |
| actions/setup-java | **v6** (v6.1.0) | 10-14 (yr inferred 2026) | https://github.com/actions/setup-java/releases | |
| gradle/actions/setup-gradle | **v6** (v6.3.0) | 08-02 (yr inferred 2026) | https://github.com/gradle/actions/releases | v6.4.0-rc.1 is a pre-release, and the `v6` tag was deliberately not moved to it. |
| actions/upload-artifact | **v7** (v7.0.2) | 10-07 (yr inferred 2026); v7.0.0 on 02-26 | https://github.com/actions/upload-artifact/releases | |

## 12. Android SDK levels and Play policy

| Item | Version | Date | Source URL | Note |
|---|---|---|---|---|
| Latest stable Android | **Android 17 = API 37** | stable 2026-06-16 | https://developer.android.com/about/versions/17 ; https://en.wikipedia.org/wiki/Android_17 ; https://www.fonearena.com/blog/?p=485177 | Official page: "Android 17 is now available", with API 36 to API 37 diffs. The dev release-notes page only lists up to Beta 4.1 (2026-06-01). The stable date comes from Wikipedia and press. QPR1/QPR2 betas use 37.1 and 37.2. |
| Android 16 | API 36 | 2025 | https://developer.android.com/tools/releases/platforms | |
| AGP 9.5 max API | 37 | - | https://developer.android.com/build/releases/gradle-plugin | compileSdk 37 is usable. Nav3 1.2.1 and the Compose 1.10-era libraries compile against 36. |
| Play target API: NEW apps and app UPDATES (phone/tablet) | **targetSdk 36 (Android 16) or higher** | in effect since **2026-08-31** | **https://support.google.com/googleplay/android-developer/answer/11926878** ; https://developer.android.com/google/play/requirements/target-sdk | The extension deadline was **2026-11-01**, which has passed as of today. Wear OS and Automotive need 35. TV and XR need 34. Existing apps must target 35 or higher to stay visible to new users on newer OS versions. |
| Next Play deadline | **Not announced** on official pages | - | same | Expect API 37 around Aug 31, 2027 by pattern, but this is unverified (see Uncertain). |

## Suggested catalog values (derived from above)

```
agp = "9.5.0"            gradle-wrapper = "9.8.2" (min 9.6.0)
kotlin = "2.4.21"        ksp = "2.3.13"
composeBom = "2026.09.00"
activityCompose = "1.13.0"   lifecycle = "2.11.0"   coreKtx = "1.19.1"   splashscreen = "1.2.0"
navigation3 = "1.2.1"        lifecycleViewmodelNav3 = "2.11.0"          (navigationCompose = "2.10.2" if Nav2)
room = "2.8.5"  (or room3 = "3.0.3")   datastore = "1.3.0"   work = "2.12.0"
hilt = "2.60.1"  androidxHilt = "1.4.0"
coroutines = "1.11.0"  serialization = "1.12.0"  datetime = "0.8.0"
firebaseBom = "35.1.0" (firebase-ai 18.1.0, appcheck 20.0.0)  googleServices = "4.5.0"
mlkitGenaiPrompt = "1.0.0-beta5"
junit = "4.13.2"  turbine = "1.2.1"  robolectric = "4.17"  spotless = "8.11.0"  ktlint = "1.8.0"
androidxTestExtJunit = "1.3.0"  espresso = "3.7.0"  androidxTest(core/runner/rules) = "1.7.0"
compileSdk = 36 or 37   targetSdk = 36 (Play minimum; 37 optional)   minSdk = 29 (above Firebase's 24 and WorkManager's 24)
JDK: toolchain 17 meets AGP; use 21 in CI for Robolectric SDK 36+
```

## Uncertain / not fully verified

- **KSP 2.3.13 release notes**: the GitHub tag page failed to load. The version and date come from Maven Central metadata (`lastUpdated` 2026-10-30). Its Kotlin 2.4.21 compatibility rests on KSP 2.3.x being Kotlin-version-independent. Kotlin's own KSP docs pair 2.4.21 with KSP 2.3.10, but no page I fetched says so explicitly.
- **"KSP2" wording for Dagger**: dagger.dev says KSP support is stable for Dagger 2.60+ with KSP 2.3.9+. It never says "KSP2". That the 2.3.x line is KSP2-only is my inference, not quoted.
- **Compose BOM 2026.09.00 to material3 1.4.0 and ui-test 1.12.1**: the mapping page's lists were matched by position because the dropdown text was flattened. material3 1.4.0 is consistent with 1.4.0 being the only stable material3 release (1.5.0 is rc01). The ui-test-junit4 row was in a truncated section.
- **AGP 9.5.0 exact day**: the page shows "October 2026" only. It most likely shipped with Rabbit 2 on Oct 28, but that is unconfirmed. AGP patch versions after 9.5.0 can't be checked because Google Maven `maven-metadata.xml` is robots-blocked.
- **Android Studio vs AGP compatibility table**: not retrieved (truncated). The preview page lists Rabbit 2 and AGP 9.5.0 together as Stable.
- **JDK bundled with Android Studio Rabbit (JBR version)**: not verified.
- **core-splashscreen 1.2.0 release date**: not shown on the page.
- **ML Kit genai-prompt minSdk and exact Gradle snippet**: not shown on the fetched pages. Check the API reference or POM before relying on minSdk 29 compatibility (likely fine, but unverified).
- **Android 17 stable date (2026-06-16)**: from Wikipedia and press (fonearena, iphoneincanada 2026-06-16). The official developer.android.com release-notes page doesn't state the final release; it shows Beta 4.1 as the last entry.
- **Next Play target-API deadline (API 37)**: not announced on the Play Console Help page or developer.android.com as of today.
- **GitHub Actions release years**: the pages showed day and month only. The year (2026) is inferred from page context. The major versions (checkout v7, setup-java v6, setup-gradle v6, upload-artifact v7) are confirmed by the "Latest" labels.
- **Navigation recommendation**: the official docs conflict. The Nav2 release page says maintenance mode, while the Navigation overview still says to use Navigation Compose for all-Compose apps. Choosing Nav3 is a judgment call based on Nav3's stable status and Nav2's maintenance mode.
