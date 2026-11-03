# Repository Guidelines

## Project Structure

LanInbox is a Kotlin Multiplatform app with shared Compose UI for Android and desktop JVM.

- `shared/src/commonMain/kotlin` contains shared UI and logic. Keep reusable UI assets in
  `shared/src/commonMain/composeResources`.
- `shared/src/androidMain` and `shared/src/jvmMain` contain platform implementations. Follow the
  existing `Platform.android.kt` and `Platform.jvm.kt` pattern for platform-specific counterparts.
- `androidApp` owns `MainActivity.kt`, the Android manifest, and `src/main/res`. `desktopApp` owns
  the desktop entry point in `main.kt` and installer configuration. Keep any embedded LAN receiver
  tied to the desktop app lifecycle.

## Build, Test, and Run

Open the repository root in Android Studio and sync Gradle. Use the checked-in wrapper from the
root. On macOS or Linux, replace `./gradlew.bat` with `./gradlew`.

| Command                                                     | Purpose                                      |
|-------------------------------------------------------------|----------------------------------------------|
| `./gradlew.bat :androidApp:assembleDebug`                   | Build the Android debug APK.                 |
| `./gradlew.bat :desktopApp:run`                             | Launch the desktop app.                      |
| `./gradlew.bat :desktopApp:hotRun --auto`                   | Launch desktop with Compose Hot Reload.      |
| `./gradlew.bat :shared:testAndroidHostTest :shared:jvmTest` | Run Android host and desktop JVM unit tests. |

Check available RAM before builds or other memory-intensive tasks. If memory is tight, stop
completed background processes you started. If headroom remains insufficient, skip the task and
report which command still needs to run.

## Style and Testing

Use four spaces and follow Kotlin conventions. Keep packages under `com.luofelix.laninbox`. Use
PascalCase for types and UI composables and lowerCamelCase for ordinary functions and properties.
Manage dependency versions in `gradle/libs.versions.toml`.

Use `kotlin.test` and name test classes `*Test` with behavior-focused methods. Put portable tests in
`shared/src/commonTest` and platform tests in `shared/src/androidHostTest` or `shared/src/jvmTest`.
Cover behavior changes with focused tests.

## Language Policy

- Use English for code identifiers, test names, API paths, JSON fields, and error codes. Write
  comments and KDoc in English, focusing on rationale, constraints, and platform differences.
  Use Chinese text in test fixtures where it helps verify Unicode handling.
- Write diagnostic and exported logs in English. Record the operation, outcome, and useful
  fields such as `messageId` and `reason=timeout`. Omit transferred content, clipboard text,
  full URLs, pairing codes, credentials, and local file paths.
- Localize user-facing text through string resources. For shared Compose UI, place default
  English strings in `shared/src/commonMain/composeResources/values/strings.xml` and Simplified
  Chinese strings in `values-zh-rCN/strings.xml` under the same resource directory. Map stable
  error codes to localized messages.
- Use English for `README.md`, `AGENTS.md`, GitHub templates, commit and PR titles, and release
  notes. Link `README.md` and `README.zh-CN.md` at the top of both files and keep setup, usage,
  and troubleshooting instructions in sync. Issue and PR bodies may use English or Chinese.
  Summarize maintenance decisions from Chinese discussions in English.

## Commits and Pull Requests

Use `type: short summary`, for example `fix: handle desktop connection timeouts`. When changing
project code, include a suggested commit message in the handoff. For bug fixes, summarize the cause
and fix. PRs should describe the change, list verification performed, link relevant issues, and
include screenshots for visible UI changes. Keep `local.properties` and generated build output out
of commits.
