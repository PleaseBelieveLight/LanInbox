# Repository Guidelines

## Project Structure

LanInbox is a Kotlin Multiplatform app with shared Compose UI for Android and desktop JVM.

- `shared/src/commonMain/kotlin` contains shared UI and logic. Keep reusable UI assets in
  `shared/src/commonMain/composeResources`.
- `shared/src/jvmAndAndroidMain/kotlin` contains the HTTPS client compiled into both JVM targets.
  Platform adapters for discovery, credential storage, and file selection live in the app modules.
- `androidApp` owns `MainActivity.kt`, the Android manifest, and `src/main/res`. `desktopApp` owns
  the desktop entry point in `main.kt` and installer configuration.

## Infrastructure Conventions

- Check for an existing infrastructure component first. If its API falls short, prefer the
  smallest extension that preserves behavior for existing callers. Add a separate component
  only when the requirement needs a distinct responsibility or lifecycle. Keep shared logic in
  one place.
- Route app diagnostics through `AppLogger.info` or `AppLogger.warn` with structured fields.
  Keep direct Logcat and console calls inside platform log sinks.
- Access preferences through `AndroidSettings` and `DesktopSettings`. Store pairing secrets
  through `AndroidPairedDeviceStore` (Android Keystore) and the DPAPI-backed Windows security
  adapters.
- Reuse `DeliveryResult`, `TransferStatus`, and `ReceiverStatus` for their respective outcomes.
  Keep mutable state in its owner and expose read-only `StateFlow` where the UI observes it.
- Define wire contracts in `core.network` and reuse `SecureLanClient` for HTTPS. Treat mDNS
  records as address hints and verify the paired public-key fingerprint during TLS.
- Keep the desktop receiver and discovery app-scoped, `DesktopTransferState` window-scoped,
  and Android transfers in `AndroidTransferViewModel`. Keep transfers independent of lazy list
  items.
- Reuse `DesktopInbox`, `DesktopAttachmentStore`, `DesktopOutbox`, and `AndroidTransferFiles`
  for storage and delivery. Preserve message IDs across retries, persist before acknowledging,
  and keep deduplication receipts when clearing history.

## Build, Test, and Run

Open the repository root in Android Studio and sync Gradle. Use the checked-in wrapper from the
root. On macOS or Linux, replace `./gradlew.bat` with `./gradlew`.

| Command                                                                      | Purpose                                                 |
|------------------------------------------------------------------------------|---------------------------------------------------------|
| `./gradlew.bat :androidApp:assembleDebug`                                    | Build the Android debug APK.                            |
| `./gradlew.bat :desktopApp:run`                                              | Launch the desktop app.                                 |
| `./gradlew.bat :desktopApp:hotRun --auto`                                    | Launch desktop with Compose Hot Reload.                 |
| `./gradlew.bat :shared:testAndroidHostTest :shared:jvmTest :desktopApp:test` | Run shared and desktop integration tests.               |
| `./gradlew.bat :desktopApp:packageMsi`                                       | Build the Windows installer with JDK 21 and WiX 3.14.1. |

Check available RAM before builds or other memory-intensive tasks. If memory is tight, stop
completed background processes you started. If headroom remains insufficient, skip the task and
report which command still needs to run.

## AI Verification and Manual Acceptance

- By default, leave visual appearance, interaction experience, screenshot comparisons, and
  device checks to the user. Also leave APK and MSI builds, installation, and launch checks to
  the user. Do not start apps or emulators, capture screenshots, or run full builds and packaging
  solely for routine acceptance unless the user requests them.
- Keep verification proportional to the change. Review the diff, check affected resource
  references and localization consistency, and run `git diff --check`. For logic changes, add or
  update focused tests as required below and run the relevant targeted tests. Purely visual or
  documentation changes do not require new automated tests.
- When changing Gradle configuration, dependencies, platform configuration, or packaging
  scripts, run the smallest relevant compilation or build check needed to validate the change.
  Avoid full builds or full test suites unless the scope or failures justify them. Apply the RAM
  check above before memory-intensive verification.
- Do not ask for confirmation each time routine acceptance is left to the user under this
  policy. If the user explicitly requests builds, tests, or acceptance checks, carry them out
  within the requested scope.
- In the handoff, state what was checked, what remains unverified, and the specific commands
  and acceptance steps the user still needs to run. Identify any pending screenshots for visible
  UI changes. Claim build success or verified behavior only when supported by completed checks.

## Style and Testing

Use four spaces and follow Kotlin conventions. Keep packages under `com.luofelix.laninbox`. Use
PascalCase for types and UI composables and lowerCamelCase for ordinary functions and properties.
Manage dependency versions in `gradle/libs.versions.toml`.

- Update Android `SharedPreferences` with the `androidx.core.content.edit` extension, for example
  `preferences.edit { putString("sender_id", it) }`.
- Pass a `Duration` to coroutine `delay` using `kotlin.time.Duration.Companion.milliseconds`, for
  example `delay(1_500.milliseconds)`.

Use `kotlin.test` and name test classes `*Test` with behavior-focused methods. Put portable tests in
`shared/src/commonTest` and platform tests in `shared/src/androidHostTest` or `shared/src/jvmTest`.
Cover behavior changes with focused tests.

## Code Comments and KDoc

- Use English KDoc for contracts callers rely on, including return values, side effects, and
  guarantees. For example, document when `DesktopInbox.save` accepts a replay and when it rejects
  a conflicting message ID. Document what `AppLogger` callers must omit from logs.
- Add short English comments next to code that enforces a non-obvious invariant or platform
  constraint. Explain why retries reuse a message ID, why the receiver acknowledges only after
  persistence, and why the request body limit allows for JSON escaping.
- Explain the rationale at network and lifecycle boundaries. Examples include disabling HTTP
  redirects, restricting cleartext access to development mode, and stopping the desktop receiver
  when its owning coroutine is canceled.
- Put cross-platform protocol contracts in developer documentation, including retry semantics,
  acknowledgment rules, and error codes. Keep that documentation and nearby comments in sync
  with behavior changes.
- Use clear names for straightforward code. Reserve comments for details a reader cannot infer
  from the code itself.

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
  notes. Issue and PR bodies may use English or Chinese. Summarize maintenance decisions from
  Chinese discussions in English.

## README Guidelines

- Treat `README.md` and `README.zh-CN.md` as the public project homepage. Link the two versions
  at the top and keep their sections, product claims, and instructions in sync.
- Open with a short, shareable introduction that explains what LanInbox lets Android and Windows
  users do. Follow it with a transfer visual, practical feature highlights, installation steps, and a
  complete transfer example. Put connection troubleshooting, project structure, and source build
  instructions after the quick start. Preserve this order unless the task calls for a change.
- Keep the steps to install, pair, transfer, troubleshoot common problems, and build from source
  in each README. Use links to other documents for optional background.
- Check feature claims and button names against the implementation and localized strings.
  Verify download instructions against release assets or the workflow that produces the builds,
  and build commands against the checked-in configuration. Identify debug builds and CI
  artifacts explicitly.
- Use demo data in screenshots and label diagrams or mockups as illustrations.
- Keep edits focused on the requested change. Before handing off, check both languages for
  consistency, verify links and images, and run `git diff --check`.

## Commits and Pull Requests

Use `type: short summary`, for example `fix: handle desktop connection timeouts`. When changing
project code, include a suggested commit message in the handoff. For bug fixes, summarize the cause
and fix. PRs should describe the change, list verification performed, link relevant issues, and
include screenshots for visible UI changes. Keep `local.properties` and generated build output out
of commits.
