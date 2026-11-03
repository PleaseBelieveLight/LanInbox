# LanInbox

[简体中文](README.zh-CN.md)

[Desktop TLS development setup](docs/desktop-tls.md)

LanInbox is a Kotlin Multiplatform app for sending text, links, and code from Android to Windows
over a local network. Shared Compose UI and protocol code live in `shared`. The app entry points
live in `androidApp` and `desktopApp`.

## Try text delivery

1. Run the Android Debug app on a device connected to the same LAN as Windows.
2. Enable the Windows development receiver in PowerShell.

   ```powershell
   $env:LANINBOX_DEV_RECEIVER = '1'
   .\gradlew.bat :desktopApp:run
   ```

3. Enter the Windows private IPv4 address in Android. The default port is `8765`. Paste text or
   share it from another app, then tap **Send**. If prompted for local network access, grant it and
   tap **Send** again.
4. Copy the received message from the Windows inbox. If delivery times out, retry with the same
   draft and address. Restart the receiver to verify that the inbox persists.

Development transfers are unauthenticated and unencrypted. Enable the receiver only on a trusted
LAN. Windows may require an inbound rule for TCP port `8765` on the private network profile. The
inbox shows the latest 100 messages and stores development history in
`%LOCALAPPDATA%/LanInbox/development-inbox.sqlite`.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these
commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
    - Hot reload: `./gradlew :desktopApp:hotRun --auto`
    - Standard run: `./gradlew :desktopApp:run`

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest :desktopApp:test`

---

Learn more
about [Kotlin Multiplatform](https://www.jetbrains.com.cn/en-us/help/kotlin-multiplatform-dev/get-started.html)…
