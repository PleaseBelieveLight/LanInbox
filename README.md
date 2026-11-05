# LanInbox

[简体中文](README.zh-CN.md)

**LanInbox is a local-network inbox for Android and Windows.** Send a link from your phone to
your PC to keep reading, or send a screenshot back to your phone. Text, links, code snippets,
images, and files go directly between the two devices on the same network.

![Transfer diagram showing a link sent from Android to Windows and a photo sent back over HTTPS](docs/assets/lan-transfer.svg)

## What you can do

- **Send a link from the Android share menu.** Share a link or text to LanInbox to open it as a
  draft, then send it to the Windows inbox.
- **Pair by QR code and send over HTTPS.** Scan the invitation on your PC to set up encrypted
  transfers in both directions.
- **Find the same PC after its IP address changes.** The pairing survives app restarts, and
  Android can rediscover the paired PC on the local network.
- **Use what you receive.** Copy text and code, preview supported images, or save files from the
  inbox.

## Install and try it

Open [Releases](https://github.com/PleaseBelieveLight/LanInbox/releases) and choose a stable
release, or select a **Pre-release** to try a test version. Download `LanInbox-<version>-android.apk`
for Android 7.0 or later and `LanInbox-<version>-windows-x64.msi` for Windows x64. The Windows
installer includes the Java runtime and is currently unsigned. Each release includes
`SHA256SUMS.txt`, build information, and the Android signing certificate details.

Until the first release is published, or to try a development build, open a successful
**Verify LanInbox** run in the **Actions** tab. Under **Artifacts**, download and unzip
`laninbox-verification-builds`. It contains a **Debug APK** and a Windows MSI.

Save any data you need before switching from a Debug APK signed with another key: Android
requires uninstalling it first, which removes app data and pairing. Older Windows verification
builds may also need to be uninstalled once before installing the first release.

### Send your first link

1. Connect the phone and PC to the same local network and open LanInbox on both.
2. On Windows, choose **Connect phone** to show a QR code. On Android, tap **Scan to connect**
   and scan the code on your PC.
3. On Android, paste `https://example.com` into the send area and tap **Send**. Copy the link
   from the Windows **Inbox**.

After pairing, use the **Image**, **Document**, or **File** icon in the Android editor, or
the **File** or **Image** icon on Windows. You can also drag one file into the Windows send area. Review the
attachment and tap **Send**. When sending to the phone,
keep LanInbox in the foreground on Android until the file appears in its inbox.

### Connection troubleshooting

- **Pair again if the phone only shows Scan to connect.** On Windows, open **Saved phone
  pairing** and choose **Pair again**, then scan the new QR code with the phone.
- **Pair manually.** Open **Connection details** on Windows and enter the LAN address,
  certificate fingerprint, and pairing invitation under **Connect manually** on Android.
- **Check local network access.** Grant permission on Android when prompted and allow LanInbox
  through Windows Firewall on private networks.
- **Reconnect after an IP change.** If discovery fails, open **Connect manually**, enter
  the PC’s current LAN address, and tap **Reconnect to paired computer**.

## Project structure

LanInbox is built with Kotlin Multiplatform and Compose Multiplatform.

- [`shared/`](shared) contains the shared UI, transfer protocol, and HTTPS client.
- [`androidApp/`](androidApp) contains the Android app and platform integrations.
- [`desktopApp/`](desktopApp) contains the Windows app, local receiver, and MSI packaging.

## Build from source

Open the repository in Android Studio and sync with JDK 21 and Android SDK Platform 37.
Set `JAVA_HOME` to JDK 21 and run these commands from the repository root in Windows PowerShell.

- Build the Android APK with `.\gradlew.bat :androidApp:assembleDebug`. Find it in
  `androidApp/build/outputs/apk/debug/`.
- For a signed Release APK, configure the environment variables in the
  [release guide](docs/releasing.md#android-signing), then run
  `.\gradlew.bat --no-configuration-cache :androidApp:assembleRelease`. Find it in
  `androidApp/build/outputs/apk/release/`.
- Launch the Windows app with `.\gradlew.bat :desktopApp:run`.
- To build a Windows MSI, install WiX 3.14.1, set `WIX_PATH` to the directory containing
  `candle.exe` and `light.exe`, then run `.\gradlew.bat :desktopApp:packageMsi`.

Maintainers: see the [release guide](docs/releasing.md) for version numbers, signing secrets,
tag-triggered release drafts, and the [manual acceptance checklist](docs/release-verification.md).
